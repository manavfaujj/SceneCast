package com.example.sources;

import android.content.Context;
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent;
import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Sunshine/GameStream PC discovery using Moonlight's bundled mDNS implementation. */
public class MoonlightPcDiscovery {
    private final Context appContext;
    private final CopyOnWriteArrayList<MdnsComputer> discovered = new CopyOnWriteArrayList<>();
    private volatile String failure;
    private final JmDNSDiscoveryAgent agent;

    public MoonlightPcDiscovery(Context context) {
        this.appContext = context.getApplicationContext();
        this.agent = new JmDNSDiscoveryAgent(appContext, new MdnsDiscoveryListener() {
            @Override
            public void notifyComputerAdded(MdnsComputer computer) {
                boolean exists = false;
                for (MdnsComputer c : discovered) {
                    if (c.getName() != null && c.getName().equals(computer.getName()) &&
                        c.getLocalAddress() != null && c.getLocalAddress().equals(computer.getLocalAddress())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    discovered.add(computer);
                }
            }

            @Override
            public void notifyDiscoveryFailure(Exception e) {
                failure = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "PC discovery failed";
            }
        });
    }

    public void start() {
        discovered.clear();
        failure = null;
        agent.startDiscovery(5000);
    }

    public void stop() {
        agent.stopDiscovery();
    }

    public List<MdnsComputer> computers() {
        return new ArrayList<>(discovered);
    }

    public String error() {
        return failure;
    }
}
