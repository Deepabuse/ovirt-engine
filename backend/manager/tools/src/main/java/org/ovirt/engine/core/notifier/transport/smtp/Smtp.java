package org.ovirt.engine.core.notifier.transport.smtp;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.ovirt.engine.core.common.EventNotificationMethod;
import org.ovirt.engine.core.notifier.dao.DispatchResult;
import org.ovirt.engine.core.notifier.filter.AuditLogEvent;
import org.ovirt.engine.core.notifier.transport.Transport;
import org.ovirt.engine.core.notifier.utils.NotificationProperties;

/**
 * The class sends e-mails to event subscribers.
 * In order to define a proper mail client, the following properties should be provided:
 * <li><code>MAIL_SERVER</code> mail server name
 * <li><code>MAIL_PORT</code> mail server port</li><br>
 * The following properties are optional: <br>
 * <li><code>MAIL_USER</code> user name includes a domain (e.g. user@test.com)</li>
 * <li><code>MAIL_PASSWORD</code> user's password</li>
 * <ul>if failed to obtain or uses "localhost" if <code>MAIL_MACHINE_NAME</code> not provided</li>
 * <li><code>MAIL_FROM</code> specifies "from" address in sent message, or uses value of property <code>MAIL_USER</code> if not provided</li>
 * <ul><li>"from" address should include a domain, same as <code>MAIL_USER</code> property
 * <li><code>MAIL_REPLY_TO</code> specifies "replyTo" address in outgoing message
 */
public class Smtp extends Transport {

    private static final String MAIL_SERVER = "MAIL_SERVER";
    private static final String MAIL_PORT = "MAIL_PORT";
    private static final String MAIL_USER = "MAIL_USER";
    private static final String MAIL_PASSWORD = "MAIL_PASSWORD";
    private static final String MAIL_FROM = "MAIL_FROM";
    private static final String MAIL_REPLY_TO = "MAIL_REPLY_TO";
    private static final String HTML_MESSAGE_FORMAT = "HTML_MESSAGE_FORMAT";
    private static final String MAIL_SMTP_ENCRYPTION = "MAIL_SMTP_ENCRYPTION";
    private static final String MAIL_SMTP_ENCRYPTION_NONE = "none";
    private static final String MAIL_SMTP_ENCRYPTION_SSL = "ssl";
    private static final String MAIL_SMTP_ENCRYPTION_TLS = "tls";
    private static final String MAIL_RETRIES = "MAIL_RETRIES";

    private static final Logger log = Logger.getLogger(Smtp.class);
    private int retries;
    private String hostName;
    private boolean isBodyHtml = false;
    private Session session = null;
    private InternetAddress from = null;
    private InternetAddress replyTo = null;
    private boolean active = false;

    public Smtp(NotificationProperties props) {
        if (!StringUtils.isEmpty(props.getProperty(MAIL_SERVER, true))) {
            active = true;
            init(props);
        }
    }

    private void init(NotificationProperties props) {
        Properties mailSessionProps =  new Properties();

        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            Smtp.log.error("Failed to resolve machine name, using localhost instead.", e);
            hostName = "localhost";
        }

        retries = props.validateNonNegetive(MAIL_RETRIES);
        isBodyHtml = props.getBoolean(HTML_MESSAGE_FORMAT, false);
        from = props.validateEmail(MAIL_FROM);
        replyTo = props.validateEmail(MAIL_REPLY_TO);

        if (log.isTraceEnabled()) {
            mailSessionProps.put("mail.debug", "true");
        }

        mailSessionProps.put("mail.smtp.host", props.getProperty(MAIL_SERVER));
        mailSessionProps.put("mail.smtp.port", props.validatePort(MAIL_PORT));

        String encryption = props.getProperty(MAIL_SMTP_ENCRYPTION);
        if (MAIL_SMTP_ENCRYPTION_NONE.equals(encryption)) {
        } else if (MAIL_SMTP_ENCRYPTION_SSL.equals(encryption)) {
            mailSessionProps.put("mail.smtp.auth", "true");
            mailSessionProps.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            mailSessionProps.put("mail.smtp.socketFactory.fallback", false);
            mailSessionProps.put("mail.smtp.socketFactory.port", props.validatePort(MAIL_PORT));
        } else if (MAIL_SMTP_ENCRYPTION_TLS.equals(encryption)) {
            mailSessionProps.put("mail.smtp.auth", "true");
            mailSessionProps.put("mail.smtp.starttls.enable", "true");
            mailSessionProps.put("mail.smtp.starttls.required", "true");
        }
        else {
            throw new IllegalArgumentException(
                String.format(
                    "Illegal encryption method for %s",
                    MAIL_SMTP_ENCRYPTION));
        }

        String emailUser = props.getProperty(MAIL_USER, true);
        String emailPassword = props.getProperty(MAIL_PASSWORD, true);
        if (StringUtils.isEmpty(emailUser) && StringUtils.isNotEmpty(emailPassword)) {
            throw new IllegalArgumentException(
                    String.format(
                            "'%s' must be set when password is set",
                            MAIL_USER));
        }
        if (StringUtils.isNotEmpty(emailPassword)) {
            session = Session.getDefaultInstance(mailSessionProps,
                new EmailAuthenticator(emailUser, emailPassword));
        } else {
            session = Session.getInstance(mailSessionProps);
        }
    }

    @Override
    public String getName() {
        return "smtp";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void dispatchEvent(AuditLogEvent event, String address) {
        if (StringUtils.isEmpty(address)) {
            log.error("Address is empty, cannot distribute message." + event.getName());
            return;
        }

        EventMessageContent message = new EventMessageContent();
        message.prepareMessage(hostName, event, isBodyHtml);

        log.info(String.format("Send email to [%s]%n subject:%n [%s]",
                address,
                message.getMessageSubject()));
        if (log.isDebugEnabled()) {
            log.debug(String.format("body:%n [%s]",
                    message.getMessageBody()));
        }

        String errorMessage = null;
        int retry = 0;
        boolean success = false;
        while (!success && retry < retries) {
            try {
                sendMail(address, message.getMessageSubject(), message.getMessageBody());
                notifyObservers(DispatchResult.success(event, address, EventNotificationMethod.EMAIL));
                success = true;
            } catch (MessagingException ex) {
                errorMessage = ex.getMessage();
            }

            if (!success) {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    log.error("Failed to suspend thread for 30 seconds while trying to resend a mail message.", e);
                }
                retry++;
            }
        }
        // Could not send after retries.
        if (!success) {
            notifyObservers(DispatchResult.failure(event, address, EventNotificationMethod.EMAIL, errorMessage));
        }
    }

    /**
     * Sends a message to a recipient using pre-configured mail session, either as a plan text message or as a html
     * message body
     * @param recipient
     *            a recipient mail address
     * @param messageSubject
     *            the subject of the message
     * @param messageBody
     *            the body of the message
     * @throws MessagingException
     */
    private void sendMail(String recipient, String messageSubject, String messageBody) throws MessagingException {
        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(from);
            InternetAddress address = new InternetAddress(recipient);
            msg.setRecipient(Message.RecipientType.TO, address);
            if (replyTo != null) {
                msg.setReplyTo(new Address[] { replyTo });
            }
            msg.setSubject(messageSubject);
            if (isBodyHtml){
                msg.setContent(String.format("<html><head><title>%s</title></head><body><p>%s</body></html>",
                        messageSubject,
                        messageBody), "text/html");
            } else {
                msg.setText(messageBody);
            }
            msg.setSentDate(new Date());
            javax.mail.Transport.send(msg);
        } catch (MessagingException mex) {
            StringBuilder errorMsg = new StringBuilder("Failed to send message ");
            if (from != null) {
                errorMsg.append(" from " + from.toString());
            }
            if (StringUtils.isNotBlank(recipient)) {
                errorMsg.append(" to " + recipient);
            }
            if (StringUtils.isNotBlank(messageSubject)) {
                errorMsg.append(" with subject " + messageSubject);
            }
            errorMsg.append(" due to to error: " + mex.getMessage());
            log.error(errorMsg.toString(), mex);
            throw mex;
        }
    }

    /**
     * An implementation of the {@link Authenticator}, holds the authentication credentials for a network connection.
     */
    private class EmailAuthenticator extends Authenticator {
        private String userName;
        private String password;
        public EmailAuthenticator(String userName, String password) {
            this.userName = userName;
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(userName, password);
        }
    }
}

