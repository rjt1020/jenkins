/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 * Copyright (c) 2020, CloudBees, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson;

import static hudson.init.InitMilestone.COMPLETED;

import hudson.init.Initializer;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Monitors a UDP multicast broadcast and respond with the location of the Hudson service.
 *
 * <p>
 * Useful for auto-discovery of Hudson in the network.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated No longer does anything.
 */
@Deprecated
@Restricted(NoExternalUse.class)
public class UDPBroadcastThread {

    // The previous default port was 33848, before the "disabled by default" change
    public static final int PORT = SystemProperties.getInteger("hudson.udp", -1);

    private static final Logger LOGGER = Logger.getLogger(UDPBroadcastThread.class.getName());

    @Initializer(before=COMPLETED)
    public static void warn() {
        if (PORT > 0) {
            LOGGER.warning("UDP broadcast capability has been removed from Jenkins. More information: https://www.jenkins.io/redirect/udp-broadcast");
        }
    }

}
