/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.security.authorization.permission;

import javax.annotation.Nonnull;

import org.apache.jackrabbit.oak.core.ImmutableTree;
import org.apache.jackrabbit.oak.core.TreeTypeProviderImpl;
import org.apache.jackrabbit.oak.plugins.nodetype.ReadOnlyNodeTypeManager;
import org.apache.jackrabbit.oak.spi.commit.CommitInfo;
import org.apache.jackrabbit.oak.spi.commit.MoveTracker;
import org.apache.jackrabbit.oak.spi.commit.Validator;
import org.apache.jackrabbit.oak.spi.commit.ValidatorProvider;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.Context;
import org.apache.jackrabbit.oak.spi.security.SecurityProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.AuthorizationConfiguration;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.PermissionConstants;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.PermissionProvider;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.Permissions;
import org.apache.jackrabbit.oak.spi.security.user.UserConfiguration;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * {@code ValidatorProvider} implementation for permission evaluation associated
 * with write operations.
 */
public class PermissionValidatorProvider extends ValidatorProvider {

    private final SecurityProvider securityProvider;
    private final AuthorizationConfiguration acConfig;
    private final long jr2Permissions;

    private final CommitInfo commitInfo;
    private final MoveTracker moveTracker;

    private ReadOnlyNodeTypeManager ntMgr;
    private Context acCtx;
    private Context userCtx;

    public PermissionValidatorProvider(SecurityProvider securityProvider, CommitInfo commitInfo) {
        this.securityProvider = securityProvider;
        this.acConfig = securityProvider.getConfiguration(AuthorizationConfiguration.class);

        ConfigurationParameters params = acConfig.getParameters();
        String compatValue = params.getConfigValue(PermissionConstants.PARAM_PERMISSIONS_JR2, null, String.class);
        jr2Permissions = Permissions.getPermissions(compatValue);

        this.commitInfo = commitInfo;
        moveTracker = commitInfo.getMoveTracker();
    }

    //--------------------------------------------------< ValidatorProvider >---
    @Nonnull
    @Override
    public Validator getRootValidator(NodeState before, NodeState after) {
        ntMgr = ReadOnlyNodeTypeManager.getInstance(after);

        PermissionProvider pp = getPermissionProvider();
        ImmutableTree treeBefore = createTree(before);
        ImmutableTree treeAfter = createTree(after);

        if (moveTracker.isEmpty()) {
            return new PermissionValidator(treeBefore, treeAfter, pp, this);
        } else {
            return new MoveAwarePermissionValidator(treeBefore, treeAfter, pp, this, moveTracker);
        }
    }

    //--------------------------------------------------------------------------

    Context getAccessControlContext() {
        if (acCtx == null) {
            acCtx = acConfig.getContext();
        }
        return acCtx;
    }

    Context getUserContext() {
        if (userCtx == null) {
            UserConfiguration uc = securityProvider.getConfiguration(UserConfiguration.class);
            userCtx = uc.getContext();
        }
        return userCtx;
    }

    ReadOnlyNodeTypeManager getNodeTypeManager() {
        return ntMgr;
    }

    boolean requiresJr2Permissions(long permission) {
        return Permissions.includes(jr2Permissions, permission);
    }

    private ImmutableTree createTree(NodeState root) {
        return new ImmutableTree(root, new TreeTypeProviderImpl(getAccessControlContext()));
    }

    private PermissionProvider getPermissionProvider() {
        return commitInfo.getPermissionProvider();
    }
}