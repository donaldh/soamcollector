/*
 * Copyright © 2016 Copyright Cisco Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package com.cisco.odl.soam.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.smiv2.mef.soam.pm.mib.rev120113.mefsoampmdmobjects.MefSoamDmHistoryStatsEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.smiv2.mef.soam.pm.mib.rev120113.mefsoampmdmobjects.MefSoamDmHistoryStatsEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.soamcollector.rev150105.GetHistoryStatsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.soamcollector.rev150105.GetHistoryStatsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.soamcollector.rev150105.GetHistoryStatsOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.soamcollector.rev150105.SoamcollectorService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.cisco.odl.soam.snmp.MibTable;
import com.google.common.util.concurrent.SettableFuture;

public class SoamProvider implements SoamcollectorService {

	private static final Logger LOG = LoggerFactory.getLogger(SoamProvider.class);

	private final DataBroker dataBroker;
	private final EventSourceRegistry eventSourceRegistry;
	private final ScheduledExecutorService scheduler;
	private Snmp snmp;

	public SoamProvider(final DataBroker dataBroker, final EventSourceRegistry eventSourceRegistry) throws IOException {
		this.dataBroker = dataBroker;
		this.eventSourceRegistry = eventSourceRegistry;
		scheduler = Executors.newScheduledThreadPool(1);
		snmp = new Snmp(new DefaultUdpTransportMapping());
		snmp.listen();
	}

	/**
	 * Method called when the blueprint container is created.
	 */
	public void init() {
		LOG.info("SoamProvider Session Initiated");
	}

	/**
	 * Method called when the blueprint container is destroyed.
	 */
	public void close() {
		scheduler.shutdown();
		LOG.info("SoamProvider Closed");
	}

	@Override
	public Future<RpcResult<GetHistoryStatsOutput>> getHistoryStats(GetHistoryStatsInput input) {
		final SettableFuture<RpcResult<GetHistoryStatsOutput>> settableFuture = SettableFuture.create();

		Runnable nonBlockingPopulateRunnable = new Runnable() {
			@Override
			public void run() {
				MibTable<MefSoamDmHistoryStatsEntryBuilder> historyTable = new MibTable<>(snmp,
						input.getIpAddress().getIpv4Address(), input.getCommunity(),
						MefSoamDmHistoryStatsEntryBuilder.class);

				GetHistoryStatsOutputBuilder outputBuilder = new GetHistoryStatsOutputBuilder();

				Map<Integer, MefSoamDmHistoryStatsEntryBuilder> historyStatsBuilders = historyTable.populate();

				List<MefSoamDmHistoryStatsEntry> historyStatsEntries = new ArrayList<>(historyStatsBuilders.size());
				for (Integer index : historyStatsBuilders.keySet()) {
					MefSoamDmHistoryStatsEntryBuilder entryBuilder = historyStatsBuilders.get(index);
					historyStatsEntries.add(entryBuilder.build());
				}

				outputBuilder.setResults(historyStatsEntries.toString());

				RpcResultBuilder<GetHistoryStatsOutput> rpcResultBuilder = RpcResultBuilder
						.success(outputBuilder.build());

				settableFuture.set(rpcResultBuilder.build());
			}
		};

		Thread nonBlockingPopulate = new Thread(nonBlockingPopulateRunnable);
		nonBlockingPopulate.start();

		return settableFuture;
	}
}