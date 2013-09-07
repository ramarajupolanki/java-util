package com.cedarsoftware.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generate a unique ID that fits within a long value quickly, will never create a duplicate value,
 * even if called insanely fast, and it incorporates part of the IP address so that machines in
 * a cluster will not create duplicates.  It guarantees no duplicates because it keeps
 * the last 100 generated, and compares those against the value generated, if it matches, it
 * will continue generating until it does not match.  It will generate 100 per millisecond without
 * matching.  Once the requests for more than 100 unique IDs per millisecond is exceeded, the
 * caller will be slowed down, because it will be retrying.  Keep in mind, 100 per millisecond is
 * 10 microseconds continuously without interruption.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) John DeRegnaucourt
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class UniqueIdGenerator
{
    private static long count = 0;
    private static final int lastIp;
    private static final Map<Long, Long> lastId = new LinkedHashMap<Long, Long>()
    {
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest)
        {
            return size() > 100;
        }
    };
    private static final Log LOG = LogFactory.getLog(UniqueIdGenerator.class);

    /**
     * Static initializer
     */
    static
    {
        byte[] ip;
        try
        {
            ip = InetAddress.getLocalHost().getAddress();
        }
        catch (UnknownHostException e)
        {
            ip = new byte[] {0, 0, 0, 0};
            LOG.warn("Failed to obtain computer's IP address", e);
        }
        lastIp = (int)ip[3] & 0xff;
    }

    public static long getUniqueId()
    {
        synchronized (UniqueIdGenerator.class)
        { // Synchronized is cluster-safe here because IP is part of ID [all IPs in
            // cluster must differ in last IP quartet]
            long newId = getUniqueIdAttempt();

            while (lastId.containsKey(newId))
            {
                newId = getUniqueIdAttempt();
            }
            lastId.put(newId, null);
            return newId;
        }
    }

    private static long getUniqueIdAttempt()
    {
        // shift time by 4 digits (so that IP and count can be last 4 digits)
        count++;
        if (count >= 100)
        {
            count = 0;
        }
        return System.currentTimeMillis() * 10000 + count * 100 + lastIp % 100;
    }
}
