/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.plugins.blob;

import javax.annotation.Nonnull;
import javax.management.openmbean.CompositeData;

/**
 * MBean for starting and monitoring the progress of
 * blob garbage collection.
 *
 * @see org.apache.jackrabbit.oak.api.jmx.RepositoryManagementMBean
 */
public interface BlobGCMBean {
    String TYPE = "BlobGarbageCollection";

    /**
     * Initiate a data store garbage collection operation.
     *
     * @param markOnly whether to only mark references and not sweep in the mark and sweep operation.
     * @return  the status of the operation right after it was initiated
     */
    CompositeData startBlobGC(boolean markOnly);

    /**
     * Data store garbage collection status
     *
     * @return  the status of the ongoing operation or if none the terminal
     * status of the last operation or <em>Status not available</em> if none.
     */
    @Nonnull
    CompositeData getBlobGCStatus();

}
