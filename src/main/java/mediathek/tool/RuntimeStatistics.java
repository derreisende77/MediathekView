package mediathek.tool;

import org.apache.logging.log4j.LogManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class RuntimeStatistics {

    public static final LocalDateTime startZeit = LocalDateTime.now();

    /**
     * Output runtime statistics to console and log file
     */
    public static void printRuntimeStatistics() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        final var endZeit = LocalDateTime.now();
        final var runTime = LocalTime.MIN.plusSeconds(Duration.between(startZeit,endZeit).toSeconds());

        var logger = LogManager.getLogger();
        logger.info("################################################################################");
        logger.info("   --> Start: {}", formatter.format(startZeit));
        logger.info("   --> Ende:  {}", formatter.format(endZeit));
        logger.info("   --> Laufzeit: {}h {}m {}s", runTime.getHour(),runTime.getMinute(),runTime.getSecond());
    }
}
