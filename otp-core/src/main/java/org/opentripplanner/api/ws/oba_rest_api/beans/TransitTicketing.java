package org.opentripplanner.api.ws.oba_rest_api.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opentripplanner.updater.ticketing.TicketingLocation;
import org.opentripplanner.updater.ticketing.TicketingProduct;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
public class TransitTicketing {
    private List<TicketingLocation> locations;
    private List<TicketingProduct> products;
}
