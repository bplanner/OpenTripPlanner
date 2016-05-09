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

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;

@NoArgsConstructor
@XmlRootElement(name="response")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlSeeAlso({TransitEntryWithReferences.class, TransitListEntryWithReferences.class})
public class TransitResponse<T> {

    @Getter private int version;
	@XmlTransient
    @Getter private Status status;
    @Getter private int code;
    @Getter private String text;
    @Getter private long currentTime = System.currentTimeMillis();
    @Getter private T data;

    public TransitResponse(int version, Status status, String text, T data) {
        this.version = version;
        this.status = status;
        this.code = status.getCode();
        this.text = text == null ? status.getText() : text;
        this.data = data;
    }
    
    public enum Status {
        NOT_MODIFIED("Not modified", HttpStatus.SC_NOT_MODIFIED),
        
        OK("OK", HttpStatus.SC_OK),
        UNKNOWN_ERROR("Error.", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        NOT_FOUND("Unknown entity.", HttpStatus.SC_NOT_FOUND),
        INVALID_VALUE("An invalid value was provided...", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        NOT_OPERATING("There is no service on the given date.", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        
        OUTSIDE_BOUNDS("OUTSIDE_BOUNDS", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        PATH_NOT_FOUND("PATH_NOT_FOUND", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        NO_TRANSIT_TIMES("NO_TRANSIT_TIMES", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        REQUEST_TIMEOUT("REQUEST_TIMEOUT", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        BOGUS_PARAMETER("BOGUS_PARAMETER", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        TOO_CLOSE("TOO_CLOSE", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        LOCATION_NOT_ACCESSIBLE("LOCATION_NOT_ACCESSIBLE", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        
        ERROR_NO_GRAPH("No graph loaded.", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        ERROR_VEHICLE_LOCATION_SERVICE("VehicleLocationService not found.", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        ERROR_BIKE_RENTAL_SERVICE("BikeRentalService not found.", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        ERROR_TICKETING_SERVICE("TicketingService not found.", HttpStatus.SC_INTERNAL_SERVER_ERROR),
        ERROR_TRANSIT_INDEX_SERVICE("No transit index found.  Add TransitIndexBuilder to your graph builder configuration and rebuild your graph.",
                HttpStatus.SC_INTERNAL_SERVER_ERROR);
        
        @Getter
        private String text;
        
        @Getter
        private int code;
        
        private Status(String text, int code) {
            this.text = text;
            this.code = code;
        }
    }
}
