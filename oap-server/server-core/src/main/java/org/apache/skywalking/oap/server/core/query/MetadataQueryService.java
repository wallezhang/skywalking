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
 *
 */

package org.apache.skywalking.oap.server.core.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.EndpointInfo;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class MetadataQueryService implements org.apache.skywalking.oap.server.library.module.Service {

    private final ModuleManager moduleManager;
    private IMetadataQueryDAO metadataQueryDAO;

    public MetadataQueryService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    public Set<String> listLayers() throws IOException {
        Set<String> layers = new HashSet<>();
        getMetadataQueryDAO().listServices(null, null).forEach(service -> {
            layers.addAll(service.getLayers());

        });
        return layers;
    }

    public List<Service> listServices(final String layer, final String group) throws IOException {
        return this.combineServices(getMetadataQueryDAO().listServices(layer, group));
    }

    public Service getService(final String serviceId) throws IOException {
        final List<Service> services = this.combineServices(getMetadataQueryDAO().getServices(serviceId));
        return services.size() > 0 ? services.get(0) : null;
    }

    public ServiceInstance getInstance(final String instanceId) throws IOException {
        return getMetadataQueryDAO().getInstance(instanceId);
    }

    public List<ServiceInstance> listInstances(final long startTimestamp, final long endTimestamp,
                                                     final String serviceId) throws IOException {
        return getMetadataQueryDAO().listInstances(startTimestamp, endTimestamp, serviceId)
                                    .stream().distinct().collect(Collectors.toList());
    }

    public List<Endpoint> findEndpoint(final String keyword, final String serviceId,
                                       final int limit) throws IOException {
        return getMetadataQueryDAO().findEndpoint(keyword, serviceId, limit)
                                    .stream().distinct().collect(Collectors.toList());
    }

    public EndpointInfo getEndpointInfo(final String endpointId) throws IOException {
        final IDManager.EndpointID.EndpointIDDefinition endpointIDDefinition = IDManager.EndpointID.analysisId(
            endpointId);
        final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(
            endpointIDDefinition.getServiceId());

        EndpointInfo endpointInfo = new EndpointInfo();
        endpointInfo.setId(endpointId);
        endpointInfo.setName(endpointIDDefinition.getEndpointName());
        endpointInfo.setServiceId(endpointIDDefinition.getServiceId());
        endpointInfo.setServiceName(serviceIDDefinition.getName());
        return endpointInfo;
    }

    private List<Service> combineServices(List<Service> services) {
        return new ArrayList<>(services.stream()
                                       .peek(service -> {
                                           if (service.getGroup() == null) {
                                               service.setGroup(Const.EMPTY_STRING);
                                           }
                                       })
                                       .collect(Collectors.toMap(Service::getName, service -> service,
                                                                 (s1, s2) -> {
                                                                     s1.getLayers().addAll(s2.getLayers());
                                                                     return s1;
                                                                 }
                                       )).values());
    }
}
