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

package org.opentripplanner.graph_builder.impl;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import lombok.Setter;
import org.onebusaway.csv_entities.EntityHandler;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.geospatial.model.XYPoint;
import org.onebusaway.geospatial.services.PolylineEncoder;
import org.onebusaway.geospatial.services.UTMLibrary;
import org.onebusaway.geospatial.services.UTMProjection;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.model.calendar.CalendarServiceData;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GenericMutableDao;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.opentripplanner.calendar.impl.MultiCalendarServiceImpl;
import org.opentripplanner.gbannotation.AgencyNameCollision;
import org.opentripplanner.graph_builder.model.GtfsBundle;
import org.opentripplanner.graph_builder.model.GtfsBundles;
import org.opentripplanner.graph_builder.services.EntityReplacementStrategy;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.graph_builder.services.GraphBuilderWithGtfsDao;
import org.opentripplanner.gtfs.GtfsContext;
import org.opentripplanner.gtfs.GtfsLibrary;
import org.opentripplanner.routing.edgetype.factory.GTFSPatternHopFactory;
import org.opentripplanner.routing.edgetype.factory.GtfsStopContext;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.services.FareServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * {@link GraphBuilder} plugin that supports adding transit network data from a GTFS feed to the
 * routing {@link Graph}.
 * 
 * Supports reading from multiple {@link GtfsReader} instances sequentially with respect to GTFS
 * entity classes. That is to say, given three feeds A, B, and C, all {@link Agency} entities will
 * be read from A, B, and C in turn, and then all {@link ShapePoint} entities will be read from A,
 * B, and C in turn, and so forth. This sequential reading scheme allows for cases where two
 * separate feeds may have cross-feed references (ex. StopTime => Stop) as facilitated by the use of
 * an {@link EntityReplacementStrategy}.
 * 
 * @author bdferris
 * 
 */
public class GtfsGraphBuilderImpl implements GraphBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(GtfsGraphBuilderImpl.class);

    private GtfsBundles _gtfsBundles;

    private EntityReplacementStrategy _entityReplacementStrategy = new EntityReplacementStrategyImpl();

    private List<GraphBuilderWithGtfsDao> gtfsGraphBuilders;

    EntityHandler counter = new EntityCounter();

    private FareServiceFactory _fareServiceFactory;

    /** will be applied to all bundles which do not have the cacheDirectory property set */
    @Setter private File cacheDirectory; 
    
    /** will be applied to all bundles which do not have the useCached property set */
    @Setter private Boolean useCached; 

    Map<Agency, GtfsBundle> agenciesSeen = new HashMap<Agency, GtfsBundle>();

    private boolean generateFeedIds = false;
    
    private static final int _boundingBufferRadiusInMeters = 3000;
    private static final int _boundingSimplifierRadiusInMeters = 500;    

    /** 
     * Construct and set bundles all at once. 
     * TODO why is there a wrapper class around a list of GTFS files?
     * TODO why is there a wrapper around GTFS files at all?
     */
    public GtfsGraphBuilderImpl (List<GtfsBundle> gtfsBundles) {
        GtfsBundles gtfsb = new GtfsBundles();
        gtfsb.setBundles(gtfsBundles);
        this.setGtfsBundles(gtfsb);
    }
    
    public GtfsGraphBuilderImpl() { };

    public List<String> provides() {
        List<String> result = new ArrayList<String>();
        result.add("transit");
        if (gtfsGraphBuilders != null) {
            for (GraphBuilderWithGtfsDao builder : gtfsGraphBuilders) {
                result.addAll(builder.provides());
            }
        }
        return result;
    }

    public List<String> getPrerequisites() {
        return Collections.emptyList();
    }

    public void setGtfsBundles(GtfsBundles gtfsBundles) {
        _gtfsBundles = gtfsBundles;
        /* check for dups */
        HashSet<String> bundles = new HashSet<String>();
        for (GtfsBundle bundle : gtfsBundles.getBundles()) {
            String key = bundle.getDataKey();
            if (bundles.contains(key)) {
                throw new RuntimeException("duplicate GTFS bundle " + key);
            }
            bundles.add(key);
        }
    }

    public void setEntityReplacementStrategy(EntityReplacementStrategy strategy) {
        _entityReplacementStrategy = strategy;
    }

    public void setFareServiceFactory(FareServiceFactory factory) {
        _fareServiceFactory = factory;
    }

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {

        MultiCalendarServiceImpl service = new MultiCalendarServiceImpl();
        GtfsStopContext stopContext = new GtfsStopContext();

        graph.setFeedIds(new ArrayList<String>(_gtfsBundles.getBundles().size()));
        
        try {
            int bundleIndex = 0;
            for (GtfsBundle gtfsBundle : _gtfsBundles.getBundles()) {
                bundleIndex += 1;
                // apply global defaults to individual GTFSBundles (if globals have been set) 
                if (cacheDirectory != null && gtfsBundle.getCacheDirectory() == null)
                    gtfsBundle.setCacheDirectory(cacheDirectory);
                if (useCached != null && gtfsBundle.getUseCached() == null)
                    gtfsBundle.setUseCached(useCached);
                GtfsMutableRelationalDao dao = new GtfsRelationalDaoImpl();
                GtfsContext context = GtfsLibrary.createContext(dao, service);
                GTFSPatternHopFactory hf = new GTFSPatternHopFactory(context);
                hf.setStopContext(stopContext);
                hf.setFareServiceFactory(_fareServiceFactory);
                hf.setMaxStopToShapeSnapDistance(gtfsBundle.getMaxStopToShapeSnapDistance());

                if (generateFeedIds && gtfsBundle.getDefaultAgencyId() == null) {
                    gtfsBundle.setDefaultAgencyId("FEED#" + bundleIndex);
                }

                loadBundle(gtfsBundle, graph, dao);

                CalendarServiceDataFactoryImpl csfactory = new CalendarServiceDataFactoryImpl();
                csfactory.setGtfsDao(dao);
                CalendarServiceData data = csfactory.createData();
                service.addData(data, dao);

                hf.setDefaultStreetToStopTime(gtfsBundle.getDefaultStreetToStopTime());
                hf.run(graph);

                if (gtfsBundle.doesTransfersTxtDefineStationPaths()) {
                    hf.createTransfersTxtTransfers();
                }
                if (gtfsBundle.isLinkStopsToParentStations()) {
                    hf.linkStopsToParentStations();
                } 
                if (gtfsBundle.isParentStationTransfers()) {
                    hf.createParentStationTransfers();
                }
                // run any additional graph builders that require the DAO
                if (gtfsGraphBuilders != null) {
                    for (GraphBuilderWithGtfsDao builder : gtfsGraphBuilders) {
                        builder.setDao(dao);
                        builder.buildGraph(graph);
                        builder.setDao(null); // clean up
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // We need to save the calendar service data so we can use it later
        CalendarServiceData data = service.getData();
        graph.putService(CalendarServiceData.class, data);
        graph.updateTransitFeedValidity(data);

    }

    /****
     * Private Methods
     ****/

    private void loadBundle(GtfsBundle gtfsBundle, Graph graph, GtfsMutableRelationalDao dao)
            throws IOException {

        StoreImpl store = new StoreImpl(dao);
        store.open();
        LOG.info("reading {}", gtfsBundle.toString());

        GtfsReader reader = new GtfsReader();
        reader.setInputSource(gtfsBundle.getCsvInputSource());
        reader.setEntitySchemaFactory(GtfsLibrary.createEntitySchemaFactory());
        reader.setEntityStore(store);

        reader.setInternStrings(true);

        if (gtfsBundle.getDefaultAgencyId() != null)
            reader.setDefaultAgencyId(gtfsBundle.getDefaultAgencyId());

        for (Map.Entry<String, String> entry : gtfsBundle.getAgencyIdMappings().entrySet())
            reader.addAgencyIdMapping(entry.getKey(), entry.getValue());

        if (LOG.isDebugEnabled())
            reader.addEntityHandler(counter);

        if (gtfsBundle.getDefaultBikesAllowed())
            reader.addEntityHandler(new EntityBikeability(true));
        
        StopToPolygonEntityHandler boundingPolyHandler = new StopToPolygonEntityHandler(_boundingBufferRadiusInMeters);        
        reader.addEntityHandler(boundingPolyHandler);

        for (Class<?> entityClass : reader.getEntityClasses()) {
            LOG.info("reading entities: " + entityClass.getName());
            reader.readEntities(entityClass);
            store.flush();
            if (entityClass == Agency.class) {
                if (reader.getDefaultAgencyId() == null) {
                    reader.setDefaultAgencyId(reader.getAgencies().get(0).getId());
                }
                for (Agency agency : reader.getAgencies()) {
                    GtfsBundle existing = agenciesSeen.get(agency);
                    if (existing != null) {
                        LOG.warn(graph.addBuilderAnnotation(new AgencyNameCollision(agency, existing.toString())));
                    } else {
                        agenciesSeen.put(agency, gtfsBundle);
                    }
                }
            }
        }
        
        updateBoundingPolygon(boundingPolyHandler, graph);

        for (ShapePoint shapePoint : store.getAllEntitiesForType(ShapePoint.class)) {
            shapePoint.getShapeId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (Route route : store.getAllEntitiesForType(Route.class)) {
            route.getId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (Stop stop : store.getAllEntitiesForType(Stop.class)) {
            stop.getId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (Trip trip : store.getAllEntitiesForType(Trip.class)) {
            trip.getId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (ServiceCalendar serviceCalendar : store.getAllEntitiesForType(ServiceCalendar.class)) {
            serviceCalendar.getServiceId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (ServiceCalendarDate serviceCalendarDate : store.getAllEntitiesForType(ServiceCalendarDate.class)) {
            serviceCalendarDate.getServiceId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (FareAttribute fareAttribute : store.getAllEntitiesForType(FareAttribute.class)) {
            fareAttribute.getId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (Pathway pathway : store.getAllEntitiesForType(Pathway.class)) {
            pathway.getId().setAgencyId(reader.getDefaultAgencyId());
        }
        for (FeedInfo feedInfo : store.getAllEntitiesForType(FeedInfo.class)) {
            graph.getFeedIds().add(String.format("%s:%s", feedInfo.getPublisherUrl(), feedInfo.getVersion()));
        }

        store.close();

    }

    public void setGtfsGraphBuilders(List<GraphBuilderWithGtfsDao> gtfsGraphBuilders) {
        this.gtfsGraphBuilders = gtfsGraphBuilders;
    }

    public List<GraphBuilderWithGtfsDao> getGtfsGraphBuilders() {
        return gtfsGraphBuilders;
    }

    private class StoreImpl implements GenericMutableDao {

        private GtfsMutableRelationalDao dao;

        StoreImpl(GtfsMutableRelationalDao dao) {
            this.dao = dao;
        }

        @Override
        public void open() {
            dao.open();
        }

        @Override
        public <T> T getEntityForId(Class<T> type, Serializable id) {
            Serializable replacement = _entityReplacementStrategy.getReplacementEntityId(type, id);
            if (replacement != null)
                id = replacement;
            return dao.getEntityForId(type, id);
        }

        @Override
        public void saveEntity(Object entity) {

            Class<? extends Object> entityType = entity.getClass();
            if (entity instanceof IdentityBean<?>
                    && _entityReplacementStrategy.hasReplacementEntities(entityType)) {
                IdentityBean<?> bean = (IdentityBean<?>) entity;
                Serializable id = bean.getId();
                if (_entityReplacementStrategy.hasReplacementEntity(entityType, id))
                    return;
            }

            dao.saveEntity(entity);
        }

        @Override
        public void flush() {
            dao.flush();
        }

        @Override
        public void close() {
            dao.close();
        }

        @Override
        public <T> void clearAllEntitiesForType(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <K extends Serializable, T extends IdentityBean<K>> void removeEntity(T entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Collection<T> getAllEntitiesForType(Class<T> type) {
            return dao.getAllEntitiesForType(type);
        }

        @Override
        public void saveOrUpdateEntity(Object entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateEntity(Object entity) {
            throw new UnsupportedOperationException();
        }
    }

    private static class EntityCounter implements EntityHandler {

        private Map<Class<?>, Integer> _count = new HashMap<Class<?>, Integer>();

        @Override
        public void handleEntity(Object bean) {
            int count = incrementCount(bean.getClass());
            if (count % 1000000 == 0)
                if (LOG.isDebugEnabled()) {
                    String name = bean.getClass().getName();
                    int index = name.lastIndexOf('.');
                    if (index != -1)
                        name = name.substring(index + 1);
                    LOG.debug("loading " + name + ": " + count);
                }
        }

        private int incrementCount(Class<?> entityType) {
            Integer value = _count.get(entityType);
            if (value == null)
                value = 0;
            value++;
            _count.put(entityType, value);
            return value;
        }

    }

    private static class EntityBikeability implements EntityHandler {

        private Boolean _defaultBikesAllowed;

        public EntityBikeability(Boolean defaultBikesAllowed) {
            _defaultBikesAllowed = defaultBikesAllowed;
        }

        @Override
        public void handleEntity(Object bean) {
            if (!(bean instanceof Trip)) {
                return;
            }

            Trip trip = (Trip) bean;
            if (_defaultBikesAllowed && trip.getRoute().getBikesAllowed() == 0) {
                trip.setBikesAllowed(1);
            }
        }
    }
    
    private static class StopToPolygonEntityHandler implements EntityHandler {

       private GeometryFactory _factory = new GeometryFactory();

       private UTMProjection _projection;

       private double _bufferRadiusInMeters;

       private Geometry _geometry;

       public StopToPolygonEntityHandler(double bufferRadiusInMeters) {
         _bufferRadiusInMeters = bufferRadiusInMeters;
       }

       public Geometry getGeometry() {
         return _geometry;
       }

       public UTMProjection getProjection() {
         return _projection;
       }

       @Override
       public void handleEntity(Object bean) {

         if (!(bean instanceof Stop)) {
             return;
         }           
         Stop stop = (Stop) bean;

         if (_projection == null) {
           int zone = UTMLibrary.getUTMZoneForLongitude(stop.getLon());
           _projection = new UTMProjection(zone);
         }

         XYPoint point = _projection.forward(new CoordinatePoint(stop.getLat(),
             stop.getLon()));

         Point p = _factory.createPoint(new Coordinate(point.getX(), point.getY()));
         Geometry geometry = p.buffer(_bufferRadiusInMeters).getEnvelope();

         if (_geometry == null)
           _geometry = geometry;
         else
           _geometry = _geometry.union(geometry);
       }
     }    
    
    public void updateBoundingPolygon(StopToPolygonEntityHandler handler, Graph graph) {
        Geometry geometry = handler.getGeometry();
        UTMProjection proj = handler.getProjection();
        List<CoordinatePoint> coords = new ArrayList<CoordinatePoint>();
        if (geometry instanceof Polygon)
          coords = getEncodedPolygon((Polygon) geometry, proj, true);
        else if (geometry instanceof MultiPolygon)
          coords = getEncodedMultiPolygon((MultiPolygon) geometry, proj, true);
        else
          LOG.error("unknown geometry: " + geometry);       

        String polyString = PolylineEncoder.createEncodings(coords).getPoints();
        graph.setBoundingPolyline(polyString);
        LOG.info("Bounding polygon for this graph {}", polyString);
    }

    private List<CoordinatePoint> getEncodedMultiPolygon(MultiPolygon multi, UTMProjection proj, boolean latFirst) {
        List<CoordinatePoint> coords = new ArrayList<CoordinatePoint>();
        for (int i = 0; i < multi.getNumGeometries(); i++)
          coords.addAll(getEncodedPolygon((Polygon)multi.getGeometryN(i), proj, latFirst));
        return coords;
    }

    private List<CoordinatePoint> getEncodedPolygon(Polygon poly, UTMProjection proj, boolean latFirst) {
        Polygon simplePoly = (Polygon)DouglasPeuckerSimplifier.simplify(poly, _boundingSimplifierRadiusInMeters);
        List<CoordinatePoint> coords = buildLineString(proj, simplePoly.getExteriorRing(), latFirst);
        for (int i = 0; i < simplePoly.getNumInteriorRing(); i++) {
          coords.addAll(buildLineString(proj, simplePoly.getInteriorRingN(i), latFirst));
        }        
        return coords;
    }
    
    private List<CoordinatePoint> buildLineString(UTMProjection proj, LineString line, boolean latFirst) {
        List<CoordinatePoint> coords = new ArrayList<CoordinatePoint>();
        for (int i = 0; i < line.getNumPoints(); i++) {
          Point point = line.getPointN(i);
          XYPoint p = new XYPoint(point.getX(), point.getY());
          coords.add(proj.reverse(p));
        }        
        return coords;
    }    
  
    @Override
    public void checkInputs() {
        for (GtfsBundle bundle : _gtfsBundles.getBundles()) {
            bundle.checkInputs();
        }
    }

    public void setGenerateFeedIds(boolean generateFeedIds) {
        this.generateFeedIds = generateFeedIds;
    }
}
