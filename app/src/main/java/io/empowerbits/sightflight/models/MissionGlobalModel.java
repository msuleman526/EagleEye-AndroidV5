package io.empowerbits.sightflight.models;

import dji.sdk.wpmz.value.mission.WaylineExitOnRCLostAction;
import dji.sdk.wpmz.value.mission.WaylineFinishedAction;

/**
 * Mission Global Configuration Model
 * Stores global mission settings like finish action and RC lost behavior
 */
public class MissionGlobalModel {

    private WaylineFinishedAction finishAction = WaylineFinishedAction.GO_HOME;
    private WaylineExitOnRCLostAction lostAction = WaylineExitOnRCLostAction.GO_BACK;

    public MissionGlobalModel() {
    }

    public MissionGlobalModel(WaylineFinishedAction finishAction, WaylineExitOnRCLostAction lostAction) {
        this.finishAction = finishAction;
        this.lostAction = lostAction;
    }

    public WaylineFinishedAction getFinishAction() {
        return finishAction;
    }

    public void setFinishAction(WaylineFinishedAction finishAction) {
        this.finishAction = finishAction;
    }

    public WaylineExitOnRCLostAction getLostAction() {
        return lostAction;
    }

    public void setLostAction(WaylineExitOnRCLostAction lostAction) {
        this.lostAction = lostAction;
    }
}