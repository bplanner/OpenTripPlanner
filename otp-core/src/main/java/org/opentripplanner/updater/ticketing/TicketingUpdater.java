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

package org.opentripplanner.updater.ticketing;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.updater.GraphUpdaterManager;
import org.opentripplanner.updater.PollingGraphUpdater;
import org.opentripplanner.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.prefs.Preferences;

import static org.opentripplanner.updater.bike_rental.GenericJSONBikeRentalDataSource.convertStreamToString;
import static org.opentripplanner.updater.ticketing.TicketingLocation.mapIntListToEnumList;
import static org.opentripplanner.updater.ticketing.TicketingLocation.mapIntToEnum;

public class TicketingUpdater extends PollingGraphUpdater {

    private static final Logger log = LoggerFactory.getLogger(TicketingUpdater.class);

    private String url;
    private Graph graph;
    private HttpUtils httpUtils;
    private long timestamp;

    @Override
    protected void runPolling() throws Exception {
        List<TicketingLocation> updatedLocations = getTicketingLocations();
        if (updatedLocations == null)
            return;

        TicketingService ticketingService = graph.getService(TicketingService.class);
        if (ticketingService == null) {
            ticketingService = new TicketingService();
            graph.putService(TicketingService.class, ticketingService);
        }

        ticketingService.updateLocations(updatedLocations);
    }

    private List<TicketingLocation> getTicketingLocations() throws Exception {
        List<TicketingLocation> locations = new ArrayList<TicketingLocation>();
        JsonNode rootNode = retrieveJson();
        if (rootNode == null) {
            return null;
        }

        if (!rootNode.isArray()) {
            throw new RuntimeException("Failed to parse ticketing response: not an array.");
        }

        Date lastModified = new Date();
        for (int i = 0; i < rootNode.size(); ++i) {
            locations.add(createTicketingLocationFromJson(rootNode.get(i), lastModified));
        }

        return locations;
    }

    private JsonNode retrieveJson() throws IOException {
        InputStream is = null;
        try {
            HttpUtils.ResultWithTimestamp data = httpUtils.getDataWithTimestamp(url, timestamp);
            if (data != null) {
                is = data.data;
                timestamp = data.timestamp;

                ObjectMapper mapper = new ObjectMapper();
                return mapper.readTree(convertStreamToString(is));
            } else {
                return null;
            }
        } catch (IOException e) {
            log.warn("Failed to parse ticketing feed from " + url + ":", e);
        } finally {
            if (is != null) {
                is.close();
            }
        }

        return null;
    }

    private TicketingLocation createTicketingLocationFromJson(JsonNode node, Date lastModified) {
        return TicketingLocation.builder()
                .visible(true)
                .id(String.valueOf(node.get("id").getIntValue()))
                .lastModified(lastModified)
                .type(mapIntToEnum(TicketingLocation.PlaceType.class, node.get("type_id").asInt()))
                .state(mapIntToEnum(TicketingLocation.TicketingState.class, node.get("state").asInt()))
                .place(node.get("place").getTextValue())
                .address(node.get("address").getTextValue())
                .description(node.get("description").getTextValue())
                .operator(node.get("organization").getTextValue())
                .lat(Double.parseDouble(node.get("lat").getTextValue()))
                .lng(Double.parseDouble(node.get("lng").getTextValue()))
                .cachAccepted(node.get("banknote").getIntValue() == 1)
                .creditCardsAccepted(node.get("creditcard").getIntValue() == 1)
                .passIdCreation(node.get("credential").getIntValue() == 1)
                .ticketPassExchange(node.get("reexchange").getIntValue() == 1)
                .openingPeriods(createOpeningPeriods(node.get("opening").get("general")))
                .products(mapIntListToEnumList(TicketingLocation.TicketProduct.class, toIntArray(node.get("products"))))
                .build();
    }

    private List<TicketingLocation.TicketingPeriod> createOpeningPeriods(JsonNode node) {
        List<TicketingLocation.TicketingPeriod> values = new ArrayList<TicketingLocation.TicketingPeriod>(node.size());
        for (int i = 0; i < node.size(); ++i) {
            values.add(createOpeningPeriod(node.get(i)));
        }
        return values;
    }

    private TicketingLocation.TicketingPeriod createOpeningPeriod(JsonNode node) {
        return TicketingLocation.TicketingPeriod.builder()
                .dayOfWeek(mapIntToEnum(TicketingLocation.DayOfWeek.class, node.get("day").asInt()))
                .opens(node.get("open").getTextValue())
                .closes(node.get("close").getTextValue())
                .build();
    }

    private List<Integer> toIntArray(JsonNode node) {
        List<Integer> values = new ArrayList<Integer>(node.size());
        for (int i = 0; i < node.size(); ++i) {
            values.add(node.get(i).asInt());
        }
        return values;
    }

    @Override
    protected void configurePolling(Graph graph, Preferences preferences) throws Exception {
        String url = preferences.get("url", null);
        if (url == null)
            throw new IllegalArgumentException("Missing mandatory 'url' parameter");

        this.url = url;
        this.graph = graph;
    }

    @Override
    public void setGraphUpdaterManager(GraphUpdaterManager updaterManager) {
    }

    @Override
    public void setup() throws Exception {
        httpUtils = new HttpUtils();
    }

    @Override
    public void teardown() {
        httpUtils.cleanup();
    }

    public String toString() {
        return "TicketingUpdaterService(" + url + ")";
    }
}
