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

package org.opentripplanner.api.ws.oba_rest_api.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class TransitStopTime implements Comparable<TransitStopTime> {

    private String stopId;
    private String stopHeadsign;
    private Long arrivalTime;
    private Long departureTime;
    private Long predictedArrivalTime;
    private Long predictedDepartureTime;
    
    public boolean hasPredictedArrivalTime() {
        return predictedArrivalTime != null;
    }
    
    public boolean hasPredictedDepartureTime() {
        return predictedDepartureTime != null;
    }
    
    public boolean hasArrivalTime() {
        return arrivalTime != null;
    }
    
    public boolean hasDepartureTime() {
        return departureTime != null;
    }

    @JsonIgnore
    public Long getSomeTime() {
        if(hasPredictedDepartureTime())
            return predictedDepartureTime;

        if(hasDepartureTime())
            return departureTime;

        if(hasPredictedArrivalTime())
            return predictedArrivalTime;

        if(hasArrivalTime())
            return arrivalTime;

        return Long.MAX_VALUE;
    }

    @Override
    public int compareTo(TransitStopTime other) {
        return Long.compare(getSomeTime(), other.getSomeTime());
    }
}
