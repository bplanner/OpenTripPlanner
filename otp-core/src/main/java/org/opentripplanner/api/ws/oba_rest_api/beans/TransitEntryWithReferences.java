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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSeeAlso;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlSeeAlso({
		TransitAgency.class, TransitAlert.class, TransitSearch.class, TransitArrivalAndDeparture.class, TransitStopWithArrivalsAndDepartures.class,
		TransitArrivalsAndDepartures.class, TransitMetadata.class, TransitRoute.class, TransitSchedule.class, TransitPolyline.class, TransitStop.class,
		TransitStopsForRoute.class, TransitTripDetails.class, TransitTripDetailsOTP.class, TransitTrip.class
})
@NoArgsConstructor
public class TransitEntryWithReferences<B> {
    @JsonProperty("class")
    @XmlAttribute(name = "class")
    private String klass;
    private boolean limitExceeded;
    private B entry;

    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    private TransitReferences references;

    public TransitEntryWithReferences(boolean limitExceeded, B entry, TransitReferences references) {
        this.klass = "entryWithReferences";
        this.limitExceeded = limitExceeded;
        this.entry = entry;
        this.references = references;
    }
}
