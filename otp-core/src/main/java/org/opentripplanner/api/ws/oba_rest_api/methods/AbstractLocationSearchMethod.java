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

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Coordinate;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.opentripplanner.api.common.SearchHintService;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.services.StreetVertexIndexService;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/stops-for-location.html">stops-for-location</a> OneBusAway API method.
 */

public abstract class AbstractLocationSearchMethod<T> extends AbstractSearchMethod<T> {

	/**
	 * The center <code>latitude</code> of the requested area.
	 */
    @QueryParam("lat")
    protected Float lat;

	/**
	 * The center <code>longitude</code> of the requested area.
	 */
    @QueryParam("lon")
    protected Float lon;

	/**
	 * The latitudinal span of the area. Optional. Override the {@code latSpan} parameter.
	 */
    @QueryParam("latSpan")
    protected Float latSpan;

	/**
	 * The longitudinal span of the area. Optional.
	 */
    @QueryParam("lonSpan")
    protected Float lonSpan;

	/**
	 * The radius (in meters) of the requested area. Optional.
	 */
	@QueryParam("radius") @DefaultValue("100")
    protected int radius;

	/**
	 * Search query with which to filter the returned results.
	 */
    @QueryParam("query")
    protected String query;

    protected List<Stop> queryStops() {
        if(query != null && latSpan == null && lonSpan == null) {
            lat = null;
            lon = null;
        }

        List<Stop> stops = new ArrayList<Stop>();
        if(lat != null && lon != null) {
            Coordinate center = new Coordinate(lon, lat);
            StreetVertexIndexService streetVertexIndexService = graph.streetIndex;
            List<org.opentripplanner.routing.vertextype.TransitStop> stopVertices;

            if (latSpan != null && lonSpan != null) {
                Coordinate c1 = new Coordinate(lon - lonSpan, lat - latSpan),
                        c2 = new Coordinate(lon + lonSpan, lat + latSpan);

                stopVertices = streetVertexIndexService.getNearbyTransitStops(c1, c2);
            } else {
                stopVertices = streetVertexIndexService.getNearbyTransitStops(center, radius);
            }

            for (org.opentripplanner.routing.vertextype.TransitStop transitStop : stopVertices) {
                stops.add(transitStop.getStop());
            }
        } else {
            stops.addAll(transitIndexService.getAllStops().values());
        }

        if(query != null && query.length() >= 3) {
            query = normalize(query);

            List<Stop> matchingRoutes = getMatchingStops(stops, query);
            stops = Lists.newArrayList(matchingRoutes);
        }

        List<Stop> filteredStops = new ArrayList<Stop>();
        for(Stop stop : stops) {
            if(GtfsLibrary.isAgencyInternal(stop))
                continue;

            filteredStops.add(stop);
        }
        stops = filteredStops;

        if(dialect.getDialect() == TransitResponseBuilder.Dialect.OBA) {
            List<Stop> filteredForStopsOnly = new ArrayList<Stop>();
            for(Stop stop : stops) {
                if(stop.getLocationType() == 0) {
                    filteredForStopsOnly.add(stop);
                }
            }
            stops = filteredForStopsOnly;
        }
        return stops;
    }

    protected List<Route> queryRoutes() {
        if(query != null && latSpan == null && lonSpan == null) {
            lat = null;
            lon = null;
        }

        List<Route> routes = new ArrayList<Route>();
        if(lat != null && lon != null) {
            Coordinate center = new Coordinate(lon, lat);
            StreetVertexIndexService streetVertexIndexService = graph.streetIndex;
            List<org.opentripplanner.routing.vertextype.TransitStop> stopVertices;

            if (latSpan != null && lonSpan != null) {
                Coordinate c1 = new Coordinate(lon - lonSpan, lat - latSpan),
                        c2 = new Coordinate(lon + lonSpan, lat + latSpan);

                stopVertices = streetVertexIndexService.getNearbyTransitStops(c1, c2);
            } else {
                stopVertices = streetVertexIndexService.getNearbyTransitStops(center, radius);
            }

            for (org.opentripplanner.routing.vertextype.TransitStop transitStop : stopVertices) {
                for(AgencyAndId routeId : getRoutesForStop(transitStop.getStopId())) {
                    routes.add(transitIndexService.getAllRoutes().get(routeId));
                }
            }
        } else if(query != null && query.length() > 0) {
            routes.addAll(transitIndexService.getAllRoutes().values());
        }

        if(query != null) {
            SearchHintService searchHintService = graph.getService(SearchHintService.class);
            query = normalize(query);

            Iterable<Route> matchingRoutes = getMatchingRoutes(routes, query, searchHintService);
            routes = Lists.newArrayList(matchingRoutes);
        }
        return routes;
    }
}
