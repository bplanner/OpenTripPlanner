/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.impl;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.patch.Alert;
import org.opentripplanner.routing.patch.AlertPatch;
import org.opentripplanner.routing.patch.Patch;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.services.PatchService;
import org.opentripplanner.util.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

@Slf4j
@Component
public class PatchServiceImpl implements PatchService {

    private Graph graph;
    
    @Autowired @Setter
    private GraphService graphService;

    private HashMap<String, Patch> patches = new HashMap<String, Patch>();
    private HashMap<String, Patch> appliedPatches = new HashMap<String, Patch>();
    private HashMap<AgencyAndId,List<Patch>> patchesByRoute = new HashMap<AgencyAndId, List<Patch>>();
    private HashMap<AgencyAndId, List<Patch>> patchesByStop = new HashMap<AgencyAndId, List<Patch>>();
    private HashMap<Alert.AppAndVersion, List<Patch>> patchesByApp = new HashMap<Alert.AppAndVersion, List<Patch>>();

    protected PatchServiceImpl() {
    }
    
    public PatchServiceImpl(Graph graph) {
        this.graph = graph;
    }

    @Override
    public Collection<Patch> getAllPatches() {
        return patches.values();
    }
    
    @Override
    public Collection<Patch> getStopPatches(AgencyAndId stop) {
        List<Patch> result = patchesByStop.get(stop);
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;
    }

    @Override
    public Collection<Patch> getRoutePatches(AgencyAndId route) {
        List<Patch> result = patchesByRoute.get(route);
        if (result == null) {
            result = Collections.emptyList();
        }
        return result;

    }

    @Override
    public Collection<Patch> getAppPatches(String apiKey, String appVersion) {
        Set<Patch> result = new HashSet<Patch>();
        Alert.AppAndVersion appAndVersion;

        appAndVersion = new Alert.AppAndVersion(apiKey, appVersion);
        if (patchesByApp.containsKey(appAndVersion)) {
            result.addAll(patchesByApp.get(appAndVersion));
        }

        appAndVersion = new Alert.AppAndVersion(apiKey, null);
        if (patchesByApp.containsKey(appAndVersion)) {
            result.addAll(patchesByApp.get(appAndVersion));
        }

        appAndVersion = new Alert.AppAndVersion(null, null);
        if (patchesByApp.containsKey(appAndVersion)) {
            result.addAll(patchesByApp.get(appAndVersion));
        }

        return result;
    }

    @Override
    public synchronized void reapplyPatches() {
        log.info("Reapplying patches...");
        Set<Patch> currentPatches = new HashSet<Patch>(appliedPatches.values());
        for(Patch patch : currentPatches)
            apply(patch);
        log.info("Reapplied patches");
    }

    @Override
    public synchronized void apply(Patch patch) {
        if (graph == null)
            graph = graphService.getGraph();

        if (patches.containsKey(patch.getId())) {
            expire(patches.get(patch.getId()));
        }

        if(patch.activeDuring(null, System.currentTimeMillis() - 48 * 60 * 60 * 1000, Long.MAX_VALUE)) {
            patch.apply(graph);
            appliedPatches.put(patch.getId(), patch);
        }

        patches.put(patch.getId(), patch);
        if (patch instanceof AlertPatch) {
            AlertPatch alertPatch = (AlertPatch) patch;
            AgencyAndId stop = alertPatch.getStop();
            if (stop != null) {
                MapUtils.addToMapList(patchesByStop, stop, patch);
            }
            AgencyAndId route = alertPatch.getRoute();
            if (route != null) {
                MapUtils.addToMapList(patchesByRoute, route, patch);
            }
            Alert.AppAndVersion appAndVersion = alertPatch.getAppAndVersion();
            if (appAndVersion != null) {
                MapUtils.addToMapList(patchesByApp, appAndVersion, patch);
            }
        }

    }

    @Override
    public void expire(Set<String> purge) {
        for (String patchId : purge) {
            if (patches.containsKey(patchId)) {
                expire(patches.get(patchId));
            }
        }

        patches.keySet().removeAll(purge);
    }

    @Override
    public void expireAll() {
        for (Patch patch : patches.values()) {
            expire(patch);
        }
        patches.clear();
    }

    @Override
    public void expireAllExcept(Set<String> retain) {
        ArrayList<String> toRemove = new ArrayList<String>();

        for (Entry<String, Patch> entry : patches.entrySet()) {
            final String key = entry.getKey();
            if (!retain.contains(key)) {
                toRemove.add(key);
                expire(entry.getValue());
            }
        }
        patches.keySet().removeAll(toRemove);
    }

    private void expire(Patch patch) {
        if (graph == null)
            graph = graphService.getGraph();

        if (patch instanceof AlertPatch) {
            AlertPatch alertPatch = (AlertPatch) patch;
            AgencyAndId stop = alertPatch.getStop();
            if (stop != null) {
                MapUtils.removeFromMapList(patchesByStop, stop, patch);
            }
            AgencyAndId route = alertPatch.getRoute();
            if (route != null) {
                MapUtils.removeFromMapList(patchesByRoute, route, patch);
            }
            Alert.AppAndVersion appAndVersion = alertPatch.getAppAndVersion();
            if (appAndVersion != null) {
                MapUtils.removeFromMapList(patchesByApp, appAndVersion, patch);
            }
        }

        if(appliedPatches.containsKey(patch.getId())) {
            patch.remove(graph);
            appliedPatches.remove(patch.getId());
        }
    }
}
