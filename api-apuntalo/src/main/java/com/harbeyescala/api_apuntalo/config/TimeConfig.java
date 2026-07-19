package com.harbeyescala.api_apuntalo.config;
import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
import java.time.Clock; import java.time.ZoneId;
@Configuration public class TimeConfig {@Bean public Clock businessClock(@Value("${app.business-zone:Europe/Madrid}")String zone){return Clock.system(ZoneId.of(zone));}}
