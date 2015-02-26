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
import java.util.List;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlSeeAlso({
		TransitAgencyWithCoverage.class, TransitRoute.class, TransitStop.class, TransitTripDetails.class, TransitVehicle.class
})
@NoArgsConstructor
public class TransitListEntryWithReferences<B> {
    @JsonProperty("class")
    @XmlAttribute(name = "class")
    private String klass;
    private List<B> list;
    boolean outOfRange = false;
    boolean limitExceeded = false;
    
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    private TransitReferences references;

    public TransitListEntryWithReferences(boolean limitExceeded, List<B> list, TransitReferences references) {
	    this.klass = "listWithReferences";
        this.limitExceeded = limitExceeded;
        this.list = list;
        this.references = references;
    }
}
