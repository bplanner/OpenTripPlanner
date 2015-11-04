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

package org.opentripplanner.api.ws.oba_rest_api.methods;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.opentripplanner.api.common.SearchHintService;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.patch.Alert;
import org.opentripplanner.routing.patch.Patch;
import org.opentripplanner.routing.services.PatchService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public abstract class AbstractSearchMethod<T> extends OneBusAwayApiMethod<T> {
    
    protected List<Route> getMatchingRoutes(Iterable<Route> routes, final String query, SearchHintService searchHintService) {
        if(!query.matches("[a-zA-Z0-9-]*"))
            return Collections.emptyList();
        
        Iterable<Route> iterableRoutes = Iterables.filter(routes, new Predicate<Route>() {
            @Override
            public boolean apply(Route route) {
                boolean matched = false;
                String routeShortName = route.getShortName() != null ? route.getShortName().toLowerCase() : null;

                if(!isInternalRequest() && GtfsLibrary.isAgencyInternal(route))
                    return false;
                
                if(routeShortName != null
                        && routeShortName.matches("" + query + "(\\D.*|)$")) {
                    matched = true;
                }
                else if(routeShortName != null
                        && routeShortName.matches("[mhd]" + query + "(\\D.*|)$")) {
                    matched = true;
                }
                else if(routeShortName != null
                        && query.matches("[mhd]")
                        && routeShortName.startsWith(query)) {
                    matched = true;
                }
                else if(routeShortName != null
                        && routeShortName.matches(".*-" + query + "(\\D.*|)$")) {
                    matched = true;
                }
                /*else if(route.getDesc() != null && route.getDesc().contains(query)) {
                    matched = true;
                }
                else if(route.getLongName() != null && route.getLongName().contains(query)) {
                    matched = true;
                }*/
                
                return matched;
            }
        });
        
        Set<Route> matchedRoutes = Sets.newHashSet(iterableRoutes);
        if(searchHintService != null) {
            for(Route route : iterableRoutes) {
                Collection<AgencyAndId> hintedRouteIds = searchHintService.getHintsForRoute(route.getId());
                for(AgencyAndId hintedRouteId : hintedRouteIds) {
                    Route hintedRoute = transitIndexService.getAllRoutes().get(hintedRouteId);
                    matchedRoutes.add(hintedRoute);
                }
            }
        }
        
        List<Route> routesList = new ArrayList<Route>(matchedRoutes);
        Collections.sort(routesList, TransitResponseBuilder.ROUTE_COMPARATOR);

        return routesList;
    }

    protected List<String> getMatchingAlerts(PatchService patchService, Iterable<Stop> stops, Iterable<Route> routes) {
        
        Set<String> alertIds = new HashSet<String>();
        
        if(stops != null) {
            for(Stop stop : stops) {
               alertIds.addAll(getAlertsForStop(patchService, stop.getId(), false));
            }
        }
        
        if(routes != null) {
            for(Route route : routes) {
                alertIds.addAll(getAlertsForRoute(patchService, route.getId()));
            }
        }
        
        return new ArrayList<String>(alertIds);
    }

    protected List<Stop> getMatchingStopsAndStations(Iterable<Stop> stops, String normalizedQuery) {
        List<Stop> list1 = getMatchingStops(stops, normalizedQuery);
        List<Stop> list2 = getMatchingStations(stops, normalizedQuery);
        if(list1 == null && list2 == null)
            return null;
        if(list1 == null)
            return list2;
        if(list2 == null)
            return list1;
        list1.addAll(list2);
        return list1;
    }

    protected List<Stop> getMatchingStations(Iterable<Stop> stops, String query) {
        if(query.length() < 3)
            return null;

        List<StopIndex> matchedStops = new LinkedList<StopIndex>();
        for(Stop stop : stops) {
            if(stop.getLocationType() != 1) {
                continue;
            }

            StopNameVariations variations = getStopNameVariationsForStop(stop);

            List<StopIndex> matches = new LinkedList<StopIndex>();
            if(variations.realStopName.contains(query)) {
                matches.add(scoreStopMatch(variations.realStopName, query, stop));
            }
            if(variations.fullStopName.contains(query)) {
                matches.add(scoreStopMatch(variations.fullStopName, query, stop));
            }
            if(variations.stopName.contains(query)) {
                matches.add(scoreStopMatch(variations.stopName, query, stop));
            }
            if(variations.subStopName.contains(query)) {
                matches.add(scoreStopMatch(variations.subStopName, query, stop));
            }
            if(variations.code != null && variations.code.equals(query)) {
                matches.add(new StopIndex(100, stop, null));
            }
            if(variations.desc != null && stop.getLocationType() == 0 && variations.desc.equals(query)) {
                matches.add(new StopIndex(100, stop, null));
            }

            for(String word : variations.words) {
                if(word.contains(query) && word.startsWith(query)) {
                    matches.add(scoreStopMatch(startingWordMultiplier, word, query, stop));
                }
                else if(word.contains(query)) {
                    matches.add(scoreStopMatch(wordMultiplier, word, query, stop));
                }
            }

            if(!matches.isEmpty()) {
                Collections.sort(matches, StopIndex.COMPARATOR);
                matchedStops.add(matches.get(0));
            }
        }

        Collections.sort(matchedStops, StopIndex.COMPARATOR);

        List<Stop> ret = new LinkedList<Stop>();
        for(StopIndex stopIndex : matchedStops) {
            if(stopIndex.getScore() < MIN_SCORE && ret.size() > MIN_RESULTS)
                continue;

            ret.add(stopIndex.getStop());
        }

        return ret;
    }

    protected List<Stop> getMatchingStops(Iterable<Stop> stops, String query) {
        if(query.length() < 3)
            return null;
        
        List<StopIndex> matchedStops = new LinkedList<StopIndex>();
        for(Stop stop : stops) {
            if(getRoutesForStop(stop.getId()).isEmpty()) {
                continue;
            }

            StopNameVariations variations = getStopNameVariationsForStop(stop);
            
            List<StopIndex> matches = new LinkedList<StopIndex>();
            if(variations.realStopName.contains(query)) {
                matches.add(scoreStopMatch(variations.realStopName, query, stop));
            }
            if(variations.fullStopName.contains(query)) {
                matches.add(scoreStopMatch(variations.fullStopName, query, stop));
            }
            if(variations.stopName.contains(query)) {
                matches.add(scoreStopMatch(variations.stopName, query, stop));
            }
            if(variations.subStopName.contains(query)) {
                matches.add(scoreStopMatch(variations.subStopName, query, stop));
            }
            if(variations.code != null && variations.code.equals(query)) {
                matches.add(new StopIndex(100, stop, null));
            }
            if(variations.desc != null && stop.getLocationType() == 0 && variations.desc.equals(query)) {
                matches.add(new StopIndex(100, stop, null));
            }
            
            for(String word : variations.words) {
                if(word.contains(query) && word.startsWith(query)) {
                    matches.add(scoreStopMatch(startingWordMultiplier, word, query, stop));
                }
                else if(word.contains(query)) {
                    matches.add(scoreStopMatch(wordMultiplier, word, query, stop));
                }
            }
            
            if(!matches.isEmpty()) {
                Collections.sort(matches, StopIndex.COMPARATOR);
                matchedStops.add(matches.get(0));
            }
        }
        
        Collections.sort(matchedStops, StopIndex.COMPARATOR);

        List<Stop> ret = new LinkedList<Stop>();
        for(StopIndex stopIndex : matchedStops) {
            if(stopIndex.getScore() < MIN_SCORE && ret.size() > MIN_RESULTS)
                continue;
            
            ret.add(stopIndex.getStop());
        }
        
        return ret;
    }

    private static final String CACHE_STOP_NAME_VARIATIONS = "stopNameVariations";
    private StopNameVariations getStopNameVariationsForStop(Stop stop) {
        StopNameVariations variations = cacheService.<Stop, StopNameVariations>get(CACHE_STOP_NAME_VARIATIONS, stop);
        if(variations != null)
            return variations;
        
        String realStopName = normalize(stop.getName());
        String fullStopName = realStopName.replaceAll(" (?:m\\+h|m|h|p+r)$", "");
        String stopName = fullStopName.replace(" \\(.*$", "");
        String subStopName = fullStopName.replace(".*? \\(", "").replaceAll("\\)$", "");
        String[] words = fullStopName.replaceAll("[()]", "").split(" ");
        String desc = stop.getDesc() != null ? stop.getDesc().toLowerCase() : null;
        String code = stop.getCode() != null ? stop.getCode().toLowerCase() : null;
        
        variations = new StopNameVariations(realStopName, fullStopName, stopName, subStopName, words, desc, code);
        cacheService.<Stop, StopNameVariations>put(CACHE_STOP_NAME_VARIATIONS, stop, variations);
        return variations;
    }
    
    @Data
    private class StopNameVariations {
        private final String realStopName;
        private final String fullStopName;
        private final String stopName;
        private final String subStopName;
        private final String[] words;
        private final String desc;
        private final String code;
    }

    // TODO: nincsenek megalapozva a számok méretei
    private int    MIN_SCORE         = 80;
    private int    MIN_RESULTS       =  5;
    private float  wordMultiplier    =  0.10F,
                   startingWordMultiplier = 0.40F,
                   stationMultiplier =  0.05F,
                   railMultiplier    =  0.10F,
                   tramMultiplier    =  0.07F,
                   otherMultiplier   =  0.03F;
    
    private StopIndex scoreStopMatch(String name, String query, Stop stop) {
        return scoreStopMatch(1.0F, name, query, stop);
    }
    
    private StopIndex scoreStopMatch(float multiplier, String name, String query, Stop stop) {
        
        float score = 100F * ((query.length() - 0.75f * name.indexOf(query)) / (float) name.length()); // minél nagyobb részre illeszkedik annál jobb

        LinkedList<Route> routes = new LinkedList<Route>();
        List<AgencyAndId> stopRoutes = getRoutesForStop(stop.getId());
        if(stop.getLocationType() == 2) {
            //for(Stop stop : transitIndexService.getStopsByParentStation())
            multiplier = stationMultiplier;
        }
        for (AgencyAndId routeId : stopRoutes) {
            Route route = transitIndexService.getAllRoutes().get(routeId);
            routes.add(route);

            if(route.getType() == 1 || route.getType() == 2) {
                multiplier += railMultiplier;
            }
            else if (route.getType() == 0) {
                multiplier += tramMultiplier;
            }
            else {
                multiplier += otherMultiplier;
            }
        }

        score *= multiplier;
        
        Collections.sort(routes, TransitResponseBuilder.ROUTE_COMPARATOR);
        return new StopIndex((int) score, stop, routes);
    }
    
    @Data
    @AllArgsConstructor
    static private class StopIndex {
        private int score;
        private Stop stop;
        private LinkedList<Route> routes;
        
        static Comparator<StopIndex> COMPARATOR = new Comparator<StopIndex>() {
            @Override
            public int compare(StopIndex a, StopIndex b) {
                int ret = b.getScore() - a.getScore();
                if(ret != 0)
                    return ret;
                
                LinkedList<Route> ra = new LinkedList<Route>(a.getRoutes());
                LinkedList<Route> rb = new LinkedList<Route>(b.getRoutes());

				ret = rb.size() - ra.size();
				if(ret != 0)
					return ret;

                return b.getStop().getId().toString().compareTo(a.getStop().getId().toString());
            }
        };
    }
    
    private final String CACHE_NORMALIZED = "normalizeStrings";
    protected String normalize(String html) {
        String normalized = cacheService.<String, String>get(CACHE_NORMALIZED, html);
        if(normalized != null) {
            return normalized;
        }
        
        normalized = html.toLowerCase().replaceAll("(?i)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        
        cacheService.<String, String>put(CACHE_NORMALIZED, html, normalized);
        return normalized; 
    }

	protected List<String> getAlertsForStop(PatchService patchService, AgencyAndId stopId, boolean activeOnly) {
		return getAlertsForStop(patchService, stopId, activeOnly, true);
	}

	protected List<String> getAlertsForStop(PatchService patchService, AgencyAndId stopId, boolean activeOnly, boolean addToReferences) {
        Set<String> alertIds = new HashSet<String>();
        
        long currentTime = System.currentTimeMillis() / 1000;
        if(patchService != null) {
            Collection<Patch> patches = patchService.getStopPatches(stopId);
            for(Patch patch : patches) {
                if(patch.activeDuring(null, currentTime, activeOnly ? currentTime : Long.MAX_VALUE)) {
                    Alert alert = patch.getAlert();
                    if(alert != null) {
	                    if(addToReferences) {
							responseBuilder.addToReferences(alert);
	                    }
                        alertIds.add(alert.alertId.toString());
                    }
                }
            }
        }
        
        return new ArrayList<String>(alertIds);
    }

    protected List<String> getAlertsForRoute(PatchService patchService, AgencyAndId routeId) {
        Set<String> alertIds = new HashSet<String>();
        
        long currentTime = System.currentTimeMillis() / 1000;
        if(patchService != null) {
            Collection<Patch> patches = patchService.getRoutePatches(routeId);
            for(Patch patch : patches) {
                if(patch.activeDuring(null, currentTime, Long.MAX_VALUE)) {
                    Alert alert = patch.getAlert();
                    if(alert != null) {
                        responseBuilder.addToReferences(alert);
                        alertIds.add(alert.alertId.toString());
                    }
                }
            }
        }
        
        return new ArrayList<String>(alertIds);
    }
}
