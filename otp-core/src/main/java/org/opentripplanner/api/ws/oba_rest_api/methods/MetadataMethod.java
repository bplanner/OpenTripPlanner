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

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.opentripplanner.api.ws.GraphMetadata;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitMetadata;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.routing.core.RoutingRequest;

import javax.ws.rs.Path;
import java.util.Calendar;
import java.util.Date;

/**
 * Implements the <a href="http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/methods/current-time.html">current-time</a> OneBusAway API method. Also returns the validity of the currently
 * used schedules.
 */

@Path(OneBusAwayApiMethod.API_BASE_PATH + "{acme : metadata|current-time}" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class MetadataMethod extends OneBusAwayApiMethod<TransitEntryWithReferences<TransitMetadata>> {

    @Override
    protected TransitResponse<TransitEntryWithReferences<TransitMetadata>> getResponse() {
        
        Date now = new Date();
        
        Calendar start = Calendar.getInstance(graph.getTimeZone());
        start.setTimeInMillis(graph.getTransitServiceStarts() * 1000);
        
        Calendar end = Calendar.getInstance(graph.getTimeZone());
        end.setTimeInMillis((graph.getTransitServiceEnds() - 1) * 1000);
        
        TransitMetadata metadata = new TransitMetadata();
        metadata.setTime(now.getTime());
        metadata.setReadableTime(ISO8601Utils.format(now));
        metadata.setValidityStart(responseBuilder.getServiceDateAsString(new ServiceDate(start)));
        metadata.setValidityEnd(responseBuilder.getServiceDateAsString(new ServiceDate(end)));
        metadata.setInternalRequest(isInternalRequest());
        
        GraphMetadata gm = getGraphMetadata();
        metadata.setLowerLeftLongitude(gm.getLowerLeftLongitude());
        metadata.setUpperRightLongitude(gm.getUpperRightLongitude());
        metadata.setLowerLeftLatitude(gm.getLowerLeftLatitude());
        metadata.setUpperRightLatitude(gm.getUpperRightLatitude());
        
        metadata.setBoundingPolyLine(gm.getBoundingPolyLine());

        long time = System.currentTimeMillis() / 1000;
        RoutingRequest options = makeTraverseOptions(time, routerId);
        metadata.setAlertIds(getAlertsForApp(options, time, time));

        metadata.setFeedIds(graph.getFeedIds());

        return responseBuilder.getResponseForMetadata(metadata);
    }

    private final String CACHE_GRAPH_METADATA = "graph-metadata";
    private GraphMetadata getGraphMetadata() {
        GraphMetadata metadata = cacheService.get(CACHE_GRAPH_METADATA, graph.hashCode());
        if(metadata == null) {
            metadata = new GraphMetadata(graph);
            cacheService.put(CACHE_GRAPH_METADATA, graph.hashCode(), metadata);
        }

        return metadata;
    }
}
