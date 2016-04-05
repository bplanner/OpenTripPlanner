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

import org.opentripplanner.api.ws.oba_rest_api.beans.TransitBikeRentalStation;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitListEntryWithReferences;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponse;
import org.opentripplanner.api.ws.oba_rest_api.beans.TransitResponseBuilder;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;
import org.opentripplanner.routing.bike_rental.BikeRentalStationService;

import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Path(OneBusAwayApiMethod.API_BASE_PATH + "bicycle-rental" + OneBusAwayApiMethod.API_CONTENT_TYPE)
public class BicycleRentalMethod extends OneBusAwayApiMethod<TransitListEntryWithReferences<TransitBikeRentalStation>> {

    @Override
    protected TransitResponse<TransitListEntryWithReferences<TransitBikeRentalStation>> getResponse() {
        BikeRentalStationService bikeRentalStationService = graph.getService(BikeRentalStationService.class);
        if(bikeRentalStationService == null) {
            return TransitResponseBuilder.getFailResponse(TransitResponse.Status.ERROR_BIKE_RENTAL_SERVICE, "Missing bike rental service.", apiVersion.getApiVersion());
        }

        Collection<BikeRentalStation> stations = bikeRentalStationService.getStations();
        List<TransitBikeRentalStation> transitBikeRentalStations = new ArrayList<TransitBikeRentalStation>();
        for(BikeRentalStation station : stations) {
            TransitBikeRentalStation transitBikeRentalStation = new TransitBikeRentalStation();
            transitBikeRentalStation.setId(station.id);
            transitBikeRentalStation.setCode(station.code);
            transitBikeRentalStation.setName(station.name);
            transitBikeRentalStation.setBikes(station.bikesAvailable);
            transitBikeRentalStation.setSpaces(station.spacesAvailable);
			transitBikeRentalStation.setType(station.type);
            transitBikeRentalStation.setLon(station.x);
            transitBikeRentalStation.setLat(station.y);

            transitBikeRentalStations.add(transitBikeRentalStation);
        }

        return responseBuilder.getResponseForList(transitBikeRentalStations);
    }
}
