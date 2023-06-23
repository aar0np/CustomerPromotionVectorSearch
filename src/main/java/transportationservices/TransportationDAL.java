package transportationservices;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.CqlVector;

public class TransportationDAL {
	private CqlSession session;
	
	public record Location(String location_id, 
			String location_name, Float similarity, Object location_vector) {
		}
	
	public record Highway(String highway_name,
			Object highway_vector) {
	}
	
	public TransportationDAL(CqlSession sess) {
		session = sess;
	}
	
	public Optional<Location> getLocation(String location_id) {
		PreparedStatement qpPrepared = session.prepare("SELECT * FROM location_vectors"
				+ " WHERE location_id = ?");
		BoundStatement qpBound = qpPrepared.bind(location_id);
		ResultSet rs = session.execute(qpBound);
		Row city = rs.one();
		
		if (city != null) {
			Location returnVal = new Location(city.getString("location_id"),
					city.getString("location_name"), 0f,
					city.getCqlVector("location_vector"));
			
			return Optional.of(returnVal);
		}
		
		return Optional.of(null);
	}
	
	public Optional<Location> getLocationByName(String location_name) {
		PreparedStatement qpPrepared = session.prepare("SELECT * FROM locations_by_name"
				+ " WHERE location_name = ?");
		BoundStatement qpBound = qpPrepared.bind(location_name);
		ResultSet rs = session.execute(qpBound);
		Row city = rs.one();
		
		if (city != null) {
			Location returnVal = new Location(city.getString("location_id"),
					city.getString("location_name"),0f,
					city.getCqlVector("location_vector"));
			
			return Optional.of(returnVal);
		}
		
		return Optional.of(null);
	}
	
	public Optional<List<Location>> getANNByVector(Object vector, int limit) {
		PreparedStatement qvPrepared = session.prepare("SELECT location_id, location_name,"
				+ " similarity_cosine(location_vector, ?) as similarity, location_vector"
				+ " FROM location_vectors"
				+ " ORDER BY location_vector ANN OF ? LIMIT " + limit);
		BoundStatement qvBound = qvPrepared.bind(vector,vector);
		ResultSet rsV = session.execute(qvBound);
		
		List<Location> returnVal = new ArrayList<>();
		
		for (Row city : rsV.all()) {
			Location loc = new Location(city.getString("location_id"),
					city.getString("location_name"), city.getFloat("similarity"),
					city.getCqlVector("location_vector"));
			
			returnVal.add(loc);
		}
		
		return Optional.of(returnVal);
	}
	
	public List<Highway> getHighwaysByCity(Location startCity, Location endCity) {
		
		List<Highway> returnVal = new ArrayList<>();
		
		// combine start and end city vector values into one List<Float>
		//String vectorsCombined = startCity.location_vector.toString() + endCity.location_vector.toString();
		//String vector = vectorsCombined.replaceAll("\\]\\[", ",");
		List<Float> vectorValuesList = new ArrayList<>();
		CqlVector<Float> startCityVector = (CqlVector<Float>) startCity.location_vector;
		CqlVector<Float> endCityVector = (CqlVector<Float>) endCity.location_vector;
		startCityVector.getValues().forEach(vectorValuesList::add);
		endCityVector.getValues().forEach(vectorValuesList::add);
		CqlVector<Float> vectorValues = CqlVector.builder().addAll(vectorValuesList).build();

		PreparedStatement qvPrepared = session.prepare("SELECT * FROM highway_vectors"
				+ " ORDER BY highway_vector ANN OF ? LIMIT 4");
		BoundStatement qvBound = qvPrepared.bind(vectorValues);
		ResultSet rsV = session.execute(qvBound);
		
		for (Row row : rsV.all()) {
			String name = row.getString("highway_name");
			Object vector = row.getCqlVector("highway_vector");
			
			Highway highway = new Highway(name,vector);
			returnVal.add(highway);
		}
		
		return returnVal;
	}
}
