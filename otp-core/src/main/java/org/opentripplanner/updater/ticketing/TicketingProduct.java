package org.opentripplanner.updater.ticketing;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Builder;

import java.util.Date;

@Getter
@ToString
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"lastModified"})
public class TicketingProduct {

    public String id;
    public String groupId;
    public String groupName;
    public String name;
    public String url;
    public String price;

    public boolean visible;
    public Date lastModified;
}
