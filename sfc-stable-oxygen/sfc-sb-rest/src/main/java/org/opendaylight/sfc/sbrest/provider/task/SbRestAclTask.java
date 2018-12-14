/*
 * Copyright (c) 2014, 2017 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.sfc.sbrest.provider.task;

import java.util.List;
import java.util.concurrent.ExecutorService;
import org.opendaylight.sfc.provider.api.SfcProviderAclAPI;
import org.opendaylight.sfc.provider.api.SfcProviderServiceClassifierAPI;
import org.opendaylight.sfc.provider.api.SfcProviderServiceForwarderAPI;
import org.opendaylight.sfc.sbrest.json.AclExporterFactory;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.acl.rev151001.access.lists.state.AccessListState;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.acl.rev151001.access.lists.state.access.list.state.AclServiceFunctionClassifier;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.scf.rev140701.service.function.classifiers.ServiceFunctionClassifier;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.scf.rev140701.service.function.classifiers.service.function.classifier.SclServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AclBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SbRestAclTask extends SbRestAbstractTask {

    private static final String ACL_REST_URI = "/config/ietf-access-control-list:access-lists/acl/";
    private static final Logger LOG = LoggerFactory.getLogger(SbRestAclTask.class);

    public SbRestAclTask(RestOperation restOperation, Acl dataObject, ExecutorService odlExecutor) {
        super(restOperation, new AclExporterFactory(), dataObject, odlExecutor);
        setRestUriList(dataObject);
    }

    public SbRestAclTask(RestOperation restOperation, Acl dataObject,
            List<SclServiceFunctionForwarder> sclServiceForwarderList, ExecutorService odlExecutor) {
        super(restOperation, new AclExporterFactory(), dataObject, odlExecutor);
        setRestUriList(dataObject, sclServiceForwarderList);
    }

    public SbRestAclTask(RestOperation restOperation, String aclName, java.lang.Class<? extends AclBase> aclType,
            List<SclServiceFunctionForwarder> sclServiceForwarderList, ExecutorService odlExecutor) {
        super(restOperation, new AclExporterFactory(), null, odlExecutor);
        setRestUriList(aclName, aclType, sclServiceForwarderList);
    }

    private void setRestUriList(Acl accessList) {
        String aclName = null;
        java.lang.Class<? extends AclBase> aclType = null;

        if (accessList != null) {
            aclName = accessList.getAclName();
            aclType = accessList.getAclType();
        }

        // rest uri list should be created from Classifier SFFs. Classifier will
        // be taken from ACL
        // operational data store <ACL, Classifier>
        // this prevents from looping through all classifiers and looking from
        // ACL.
        AccessListState accessListState = SfcProviderAclAPI.readAccessListState(aclName, aclType);
        if (accessListState != null) {
            List<AclServiceFunctionClassifier> serviceClassifierList = accessListState
                    .getAclServiceFunctionClassifier();

            // loop through all classifiers listed in ACL State and get REST
            // URIs from the
            // Classifier's SFFs
            if (serviceClassifierList != null) {
                for (AclServiceFunctionClassifier aclServiceClassifier : serviceClassifierList) {
                    ServiceFunctionClassifier serviceClassifier = SfcProviderServiceClassifierAPI
                            .readServiceClassifier(aclServiceClassifier.getName());

                    if (serviceClassifier != null) {
                        List<SclServiceFunctionForwarder> sclServiceForwarderList = serviceClassifier
                                .getSclServiceFunctionForwarder();
                        setRestUriList(aclName, aclType, sclServiceForwarderList);
                    }
                }
            }
        }
    }

    private void setRestUriList(Acl accessList, List<SclServiceFunctionForwarder> sclServiceForwarderList) {
        if (accessList != null) {
            setRestUriList(accessList.getAclName(), accessList.getAclType(), sclServiceForwarderList);
        }
    }

    private void setRestUriList(String aclName, java.lang.Class<? extends AclBase> aclType,
            List<SclServiceFunctionForwarder> sclServiceForwarderList) {
        // rest uri list should be created from Classifier SFFs. Classifier will
        // be taken from ACL
        if (sclServiceForwarderList != null && aclName != null && aclType != null) {
            for (SclServiceFunctionForwarder sclServiceForwarder : sclServiceForwarderList) {
                SffName sffName = new SffName(sclServiceForwarder.getName());
                ServiceFunctionForwarder serviceForwarder = SfcProviderServiceForwarderAPI
                        .readServiceFunctionForwarder(sffName);

                if (serviceForwarder != null && serviceForwarder.getRestUri() != null
                        && !serviceForwarder.getRestUri().getValue().isEmpty()) {
                    String restUri = serviceForwarder.getRestUri().getValue() + ACL_REST_URI + aclName;
                    addRestUri(restUri);
                    LOG.info("ACL will be send to REST URI {}", restUri);
                }
            }
        }
    }
}
