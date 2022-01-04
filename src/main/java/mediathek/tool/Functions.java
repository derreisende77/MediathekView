package mediathek.tool;

import org.apache.commons.lang3.ArchUtils;
import org.apache.commons.lang3.SystemUtils;

public class Functions {
    /**
     * Detect and return the currently used operating system.
     *
     * @return The enum for supported Operating Systems.
     */
    public static OperatingSystemType getOs() {
        OperatingSystemType os = OperatingSystemType.UNKNOWN;

        if (SystemUtils.IS_OS_WINDOWS) {
            if (ArchUtils.getProcessor().is64Bit())
                os = OperatingSystemType.WIN64;
            else
                os = OperatingSystemType.WIN32;
        } else if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_FREE_BSD)
            os = OperatingSystemType.LINUX; //This is a hack...
        else if (SystemUtils.IS_OS_MAC_OSX) {
            os = OperatingSystemType.MAC;
        }
        return os;
    }

    public static String getOsString() {
        return getOs().toString();
    }

}
