/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.registry.server.session.node;

import com.alipay.sofa.registry.common.model.Node.NodeType;
import com.alipay.sofa.registry.common.model.metaserver.NodeChangeResult;
import com.alipay.sofa.registry.common.model.metaserver.ReNewNodesRequest;
import com.alipay.sofa.registry.common.model.metaserver.SessionNode;
import com.alipay.sofa.registry.common.model.store.URL;
import com.alipay.sofa.registry.log.Logger;
import com.alipay.sofa.registry.log.LoggerFactory;
import com.alipay.sofa.registry.net.NetUtil;
import com.alipay.sofa.registry.remoting.exchange.RequestException;
import com.alipay.sofa.registry.remoting.exchange.message.Request;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author shangyu.wh
 * @version $Id: SessionNodeManager.java, v 0.1 2018-03-05 10:42 shangyu.wh Exp $
 */
public class SessionNodeManager extends AbstractNodeManager<SessionNode> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionNodeManager.class);

    @Override
    public SessionNode getNode(String dataInfoId) {
        return null;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.SESSION;
    }

    public List<String> getZoneServerList(String zonename) {
        List<String> serverList = new ArrayList<>();
        Collection<SessionNode> sessionNodes = getDataCenterNodes();
        if (sessionNodes != null && !sessionNodes.isEmpty()) {
            if (zonename == null || zonename.isEmpty()) {
                zonename = sessionServerConfig.getSessionServerRegion();
            }
            for (SessionNode sessionNode : sessionNodes) {
                if (zonename.equals(sessionNode.getRegionId())) {
                    URL url = sessionNode.getNodeUrl();
                    if (url != null) {
                        serverList.add(url.getIpAddress());
                    }
                }
            }

        }
        return serverList;
    }

    @Override
    public void reNewNode() {
        try {

            Request<ReNewNodesRequest> reNewNodesRequestRequest = new Request<ReNewNodesRequest>() {

                @Override
                public ReNewNodesRequest getRequestBody() {
                    URL clientUrl = new URL(NetUtil.getLocalAddress().getHostAddress(), 0);
                    SessionNode sessionNode = new SessionNode(clientUrl,
                        sessionServerConfig.getSessionServerRegion());

                    return new ReNewNodesRequest(sessionNode);
                }

                @Override
                public URL getRequestUrl() {
                    return new URL(raftClientManager.getLeader().getIp(),
                        sessionServerConfig.getMetaServerPort());
                }
            };

            metaNodeExchanger.request(reNewNodesRequestRequest);
        } catch (RequestException e) {
            LOGGER.error("SessionNodeManager reNew node error! " + e.getRequestMessage(), e);
            throw new RuntimeException("SessionNodeManager reNew node error! "
                                       + e.getRequestMessage(), e);
        }
    }

    @Override
    public void updateNodes(NodeChangeResult nodeChangeResult) {
        write.lock();
        try {
            Long receiveVersion = nodeChangeResult.getVersion();
            boolean versionChange = checkAndUpdateListVersions(
                sessionServerConfig.getSessionServerDataCenter(), receiveVersion);
            if (!versionChange) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Current data type {} list version has not updated!",
                        getNodeType());
                }
            }
            nodes = nodeChangeResult.getNodes();
        } finally {
            write.unlock();
        }
    }
}