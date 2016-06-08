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

import lombok.AllArgsConstructor;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import org.opentripplanner.routing.edgetype.TicketBuyingEdge;
import org.opentripplanner.routing.edgetype.loader.LinkRequest;
import org.opentripplanner.routing.edgetype.loader.NetworkLinkerLibrary;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.vertextype.TicketingLocationVertex;
import org.opentripplanner.updater.GraphUpdaterManager;
import org.opentripplanner.updater.GraphWriterRunnable;
import org.opentripplanner.updater.PollingGraphUpdater;
import org.opentripplanner.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

import static org.opentripplanner.updater.bike_rental.GenericJSONBikeRentalDataSource.convertStreamToString;
import static org.opentripplanner.updater.ticketing.TicketingLocation.mapIntToEnum;

public class TicketingUpdater extends PollingGraphUpdater {

    private static final Logger log = LoggerFactory.getLogger(TicketingUpdater.class);

    private NetworkLinkerLibrary networkLinkerLibrary;
    private GraphUpdaterManager updaterManager;
    private TicketingService service;
    private Graph graph;

    private String url;
    private String defaultAgencyId;
    private HttpUtils httpUtils;
    private long timestamp;

    private Map<TicketingLocation.DayOfWeek, AgencyAndId> serviceIdMap = new HashMap<TicketingLocation.DayOfWeek, AgencyAndId>();

    private Map<TicketingLocation, TicketingLocationVertex> verticesByStation = new HashMap<TicketingLocation, TicketingLocationVertex>();

    @Override
    public void setGraphUpdaterManager(GraphUpdaterManager updaterManager) {
        this.updaterManager = updaterManager;
    }

    @Override
    protected void runPolling() throws Exception {
        JsonNode rootNode = retrieveJson();
        if (rootNode == null) {
            return;
        }

        List<TicketingLocation> updatedLocations = getTicketingLocations(rootNode);
        List<TicketingProduct> updatedProducts = getTicketingProducts(rootNode);
        if (updatedLocations == null || updatedProducts == null)
            return;

        // Create graph writer runnable to apply these locations to the graph
        TicketingGraphWriterRunnable graphWriterRunnable = new TicketingGraphWriterRunnable(updatedLocations, updatedProducts);
        updaterManager.execute(graphWriterRunnable);
    }

    @Override
    protected void configurePolling(Graph graph, Preferences preferences) throws Exception {
        String url = preferences.get("url", null);
        if (url == null)
            throw new IllegalArgumentException("Missing mandatory 'url' parameter");

        this.defaultAgencyId = preferences.get("defaultAgencyId", null);
        this.url = url;
        this.graph = graph;

        CalendarService calendarService = graph.getCalendarService();
        for (TicketingLocation.DayOfWeek dayOfWeek : TicketingLocation.DayOfWeek.values()) {
            AgencyAndId serviceId = new AgencyAndId(defaultAgencyId, "DAY_" + dayOfWeek.name());
            if(calendarService.getServiceIds().contains(serviceId)) {
                serviceIdMap.put(dayOfWeek, serviceId);
            }
        }
    }

    @Override
    public void setup() throws InterruptedException, ExecutionException {
        httpUtils = new HttpUtils();

        // Creation of network linker library will not modify the graph
        networkLinkerLibrary = new NetworkLinkerLibrary(graph,
                Collections.<Class<?>, Object>emptyMap());

        // Adding a bike rental station service needs a graph writer runnable
        updaterManager.executeBlocking(new GraphWriterRunnable() {
            @Override
            public void run(Graph graph) {
                service = graph.getService(TicketingService.class);
                if (service == null) {
                    service = new TicketingService(serviceIdMap);
                    graph.putService(TicketingService.class, service);
                }
            }
        });
    }

    @Override
    public void teardown() {
        httpUtils.cleanup();
    }

    private List<TicketingLocation> getTicketingLocations(JsonNode rootNode) throws Exception {
        List<TicketingLocation> locations = new ArrayList<TicketingLocation>();
        if (!rootNode.isObject()) {
            throw new RuntimeException("Failed to parse ticketing response: not an object.");
        }

        JsonNode locationsNode = rootNode.get("locations");
        if (locationsNode == null || !locationsNode.isArray()) {
            throw new RuntimeException("Failed to parse ticketing locations.");
        }

        for (int i = 0; i < locationsNode.size(); ++i) {
            locations.add(createTicketingLocationFromJson(locationsNode.get(i)));
        }

        return locations;
    }

    private List<TicketingProduct> getTicketingProducts(JsonNode rootNode) throws Exception {
        List<TicketingProduct> products = new ArrayList<TicketingProduct>();
        if (!rootNode.isObject()) {
            throw new RuntimeException("Failed to parse ticketing response: not an object.");
        }

        JsonNode productsNode = rootNode.get("products");
        if (productsNode == null || !productsNode.isArray()) {
            throw new RuntimeException("Failed to parse ticketing products.");
        }

        for (int i = 0; i < productsNode.size(); ++i) {
            products.add(createTicketingProductFromJson(productsNode.get(i)));
        }

        return products;
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

    private TicketingProduct createTicketingProductFromJson(JsonNode node) {
        return TicketingProduct.builder()
                .id(node.get("id").getTextValue())
                .visible(!node.has("visible") || node.get("visible").getBooleanValue())
                .lastModified(new Date(node.get("modified").getLongValue() * 1000))
                .name(node.get("name").asText())
                .price(node.get("price").asText())
                .url(node.get("url").asText())
                .groupId(node.get("groupId").asText())
                .groupName(node.get("groupName").asText())
                .build();
    }

    private TicketingLocation createTicketingLocationFromJson(JsonNode node) {
        return TicketingLocation.builder()
                .id(node.get("id").getTextValue())
                .visible(!node.has("visible") || node.get("visible").getBooleanValue())
                .lastModified(new Date(node.get("modified").getLongValue() * 1000))
                .type(mapIntToEnum(TicketingLocation.PlaceType.class, node.get("type_id").asInt()))
                .state(mapIntToEnum(TicketingLocation.TicketingState.class, node.get("state").asInt()))
                .place(node.get("place").getTextValue())
                .address(node.get("address").getTextValue())
                .description(node.get("description").getTextValue())
                .operator(node.get("organization").getTextValue())
                .lat(Double.parseDouble(node.get("lat").getTextValue()))
                .lon(Double.parseDouble(node.get("lng").getTextValue()))
                .cashAccepted(node.get("banknote").getIntValue() == 1)
                .creditCardsAccepted(node.get("creditcard").getIntValue() == 1)
                .passIdCreation(node.get("credential").getIntValue() == 1)
                .ticketPassExchange(node.get("reexchange").getIntValue() == 1)
                .openingPeriods(createOpeningPeriods(node.get("opening").get("general")))
                .products(toStringArray(node.get("products")))
                .build();
    }

    private List<TicketingLocation.TicketingPeriod> createOpeningPeriods(JsonNode node) {
        List<TicketingLocation.TicketingPeriod> values = new ArrayList<TicketingLocation.TicketingPeriod>(node.size());
        for (int i = 0; i < node.size(); ++i) {
            values.add(createOpeningPeriod(node.get(i)));
        }

        boolean isOpen247 = values.size() == 8;
        for (int i = 0; i < values.size() && isOpen247; ++i) {
            TicketingLocation.TicketingPeriod period = values.get(i);
            isOpen247 = TicketingLocation.DayOfWeek.values()[i] == period.getDayOfWeek()
                    && "00:00".equals(period.getOpens())
                    && ("23:59".equals(period.getCloses()) || "24:00".equals(period.getCloses()));
        }

        if (isOpen247) {
            return Collections.singletonList(TicketingLocation.TicketingPeriod.builder().dayOfWeek(TicketingLocation.DayOfWeek.O247).build());
        } else {
            return values;
        }
    }

    private TicketingLocation.TicketingPeriod createOpeningPeriod(JsonNode node) {
        return TicketingLocation.TicketingPeriod.builder()
                .dayOfWeek(mapIntToEnum(TicketingLocation.DayOfWeek.class, node.get("day").asInt()))
                .opens(node.get("open").getTextValue())
                .closes(node.get("close").getTextValue())
                .build();
    }

    private List<String> toStringArray(JsonNode node) {
        List<String> values = new ArrayList<String>(node.size());
        for (int i = 0; i < node.size(); ++i) {
            values.add(node.get(i).asText());
        }
        return values;
    }

    public String toString() {
        return "TicketingUpdaterService(" + url + ")";
    }

    @AllArgsConstructor
    private class TicketingGraphWriterRunnable implements GraphWriterRunnable {

        private List<TicketingLocation> locations;
        private List<TicketingProduct> products;

        @Override
        public void run(Graph graph) {
            Set<TicketingLocation> stationSet = new HashSet<TicketingLocation>();
            for (TicketingLocation location : locations) {
                location = service.addLocation(location);
                if (!location.visible) {
                    continue;
                }

                TicketingLocationVertex vertex = verticesByStation.get(location);
                stationSet.add(location);

                if (vertex == null) {
                    vertex = new TicketingLocationVertex(graph, location);
                    LinkRequest request = networkLinkerLibrary.connectVertexToStreets(vertex);
                    for (Edge e : request.getEdgesAdded()) {
                        graph.addTemporaryEdge(e);
                    }
                    verticesByStation.put(location, vertex);
                    new TicketBuyingEdge(serviceIdMap, vertex, vertex);
                } else {
                    vertex.setLocation(location);
                }
            }

            List<TicketingLocation> toRemove = new ArrayList<TicketingLocation>();
            for (Map.Entry<TicketingLocation, TicketingLocationVertex> entry : verticesByStation.entrySet()) {
                TicketingLocation station = entry.getKey();
                if (stationSet.contains(station))
                    continue;

                TicketingLocationVertex vertex = entry.getValue();
                if (graph.containsVertex(vertex)) {
                    graph.removeVertexAndEdges(vertex);
                }
                toRemove.add(station);
            }

            for (TicketingLocation station : toRemove) {
                verticesByStation.remove(station);
                //service.removeLocation(station);
            }

            service.updateProducts(products);
        }
    }
}
