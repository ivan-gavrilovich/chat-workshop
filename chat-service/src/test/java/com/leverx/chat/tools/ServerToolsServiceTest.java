package com.leverx.chat.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerToolsServiceTest {

    private final ServerToolsService service = new ServerToolsService();

    @Test
    void resourceLoad_returnsCpuAndRam() {
        var load = service.getServerResourceLoad();
        assertThat(load.cpuLoadPercent()).isBetween(0.0, 100.0);
        assertThat(load.usedRamMb()).isPositive();
        assertThat(load.totalRamMb()).isGreaterThanOrEqualTo(load.usedRamMb());
        assertThat(load.ramUsedPercent()).isBetween(0.0, 100.0);
    }

    @Test
    void recentErrorReports_returnsFiveEntries() {
        var reports = service.getRecentErrorReports();
        assertThat(reports).hasSize(5);
        assertThat(reports).allSatisfy(r -> {
            assertThat(r.timestamp()).isNotBlank();
            assertThat(r.level()).isNotBlank();
            assertThat(r.component()).isNotBlank();
            assertThat(r.message()).isNotBlank();
        });
    }
}
