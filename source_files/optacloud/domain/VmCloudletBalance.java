package com.optacloud.domain;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.List;

@PlanningSolution
public class VmCloudletBalance {

    private List<Vm> vmlist;

    private List<Cloudlet> cloudletlist;

    private HardSoftScore score;

    public VmCloudletBalance() {
    }

    public VmCloudletBalance(List<Vm> vmlist, List<Cloudlet> cloudletlist) {
        this.vmlist = vmlist;
        this.cloudletlist = cloudletlist;
    }

    @ValueRangeProvider(id = "vmRange")
    public List<Vm> getVmlist() {
        return vmlist;
    }

    @PlanningEntityCollectionProperty
    public List<Cloudlet> getCloudletlist() { return cloudletlist; }

    public void setVmlist(List<Vm> vmlist) {
        this.vmlist = vmlist;
    }

    public void setCloudletlist(List<Cloudlet> cloudletlist) {
        this.cloudletlist = cloudletlist;
    }

    @PlanningScore
    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
