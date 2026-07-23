package com.butchercraft.world.allocation;

record AllocationPublishedCycle(
        AllocationRegistry definitions,
        AllocationRuntimeRegistry runtimes,
        AllocationReportRegistry reports,
        AllocationHistory history,
        AllocationCycleTraceRegistry traces
) {
    AllocationPublishedCycle {
        definitions = AllocationValidation.required(definitions, "definitions");
        runtimes = AllocationValidation.required(runtimes, "runtimes");
        reports = AllocationValidation.required(reports, "reports");
        history = AllocationValidation.required(history, "history");
        traces = AllocationValidation.required(traces, "traces");
    }
}
