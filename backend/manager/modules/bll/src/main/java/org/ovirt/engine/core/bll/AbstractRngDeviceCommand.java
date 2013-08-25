package org.ovirt.engine.core.bll;

import java.util.ArrayList;
import java.util.List;
import org.ovirt.engine.core.bll.utils.PermissionSubject;
import org.ovirt.engine.core.common.FeatureSupported;
import org.ovirt.engine.core.common.VdcObjectType;
import org.ovirt.engine.core.common.action.RngDeviceParameters;
import org.ovirt.engine.core.common.businessentities.VDSGroup;
import org.ovirt.engine.core.common.businessentities.VmBase;
import org.ovirt.engine.core.common.businessentities.VmDevice;
import org.ovirt.engine.core.common.businessentities.VmDeviceGeneralType;
import org.ovirt.engine.core.common.errors.VdcBllMessages;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.VmDeviceDAO;

/**
 * Base class for crud for random number generator devices
 */
public abstract class AbstractRngDeviceCommand<T extends RngDeviceParameters> extends CommandBase<T>  {

    private VmBase cachedEntity = null;
    private List<VmDevice> cachedRngDevices = null;

    protected AbstractRngDeviceCommand(T parameters) {
        super(parameters);

        if (parameters.getRngDevice() == null || parameters.getRngDevice().getVmId() == null) {
            return;
        }

        Guid vmId = parameters.getRngDevice().getVmId();
        setVmId(vmId);

        cachedEntity = getParameters().isVm()
                ? getVmStaticDAO().get(vmId)
                : getVmTemplateDAO().get(vmId);
        if (cachedEntity != null) {
            setVdsGroupId(cachedEntity.getVdsGroupId());
        }

        cachedRngDevices = new ArrayList<>();
        List<VmDevice> rngDevs = getVmDeviceDao().getVmDeviceByVmIdAndType(vmId, VmDeviceGeneralType.RNG);
        if (rngDevs != null) {
            cachedRngDevices.addAll(rngDevs);
        }
    }

    @Override
    public List<PermissionSubject> getPermissionCheckSubjects() {
        List<PermissionSubject> permissionList = new ArrayList<PermissionSubject>();
        permissionList.add(new PermissionSubject(getParameters().getRngDevice().getVmId(),
                getParameters().isVm() ? VdcObjectType.VM : VdcObjectType.VmTemplate,
                getActionType().getActionGroup()));
        return permissionList;
    }

    @Override
    protected boolean canDoAction() {
        if (getParameters().getRngDevice().getVmId() == null || cachedEntity == null) {
            return failCanDoAction(getParameters().isVm() ? VdcBllMessages.ACTION_TYPE_FAILED_VM_NOT_FOUND
                    : VdcBllMessages.ACTION_TYPE_FAILED_TEMPLATE_DOES_NOT_EXIST);
        }

        if (getParameters().isVm() && getVm() != null && getVm().isRunningOrPaused()) {
            return failCanDoAction(VdcBllMessages.ACTION_TYPE_FAILED_VM_IS_RUNNING);
        }

        return true;
    }

    protected boolean isRngSupportedByClusterLevel() {
        VDSGroup cluster = getVdsGroup();
        return cluster != null && FeatureSupported.virtIoRngSupported(cluster.getcompatibility_version());
    }

    protected List<VmDevice> getRngDevices() {
        return cachedRngDevices;
    }

    protected VmDeviceDAO getVmDeviceDao() {
        return getDbFacade().getVmDeviceDao();
    }

}
