package com.datastax.customerpromotion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.data.CqlVector;

import transportationservices.CqlVectorSerializable;
import transportationservices.TransportationDAL;
import transportationservices.TransportationDAL.Location;
import transportationservices.TransportationDAL.Highway;

import astraconnect.AstraConnection;

@RequestMapping("/transportsvc")
@RestController
public class TransportationSvcController {
	private CqlSession session;
	private TransportationDAL transportDAL;
	
	private record HighwayResponse (List<Highway> highways,
			Set<String> citiesOnRoute) {
	};
	
	public TransportationSvcController() {
		AstraConnection conn = new AstraConnection();
		session = conn.getCqlSession();
		transportDAL = new TransportationDAL(session);
	}
	
	@GetMapping("/city/{cityid}")
	public ResponseEntity<Optional<Location>> getCity(
			HttpServletRequest req,
            @PathVariable(value = "cityid") 
            String cityId) {
		
		Optional<Location> city = transportDAL.getLocation(cityId);
		
		return ResponseEntity.ok(city);
	}
	
	@PostMapping("/citylist/{startingcityid}")
	public ResponseEntity<Stream<String>> computeCityList(
			HttpServletRequest req,
			@RequestBody List<String> cityList,
            @PathVariable(value = "startingcityid") 
            String startingCityId) {
		
		List<String> cityPathInOrder = new ArrayList<>();
		List<String> notYetVisited = cityList;
		int limit = cityList.size();
		
		// get starting city detail
		Optional<Location> city = transportDAL.getLocation(startingCityId);
		
		// traverse cities
		while (notYetVisited.size() > 0) {
			Object cityVector = city.get().location_vector();
			String cityName = city.get().location_name();
			Optional<List<Location>> locationANNs = transportDAL.getANNByVector(cityVector, limit);
			
			// examine each city in order by ANN, check if we've already been there
			// if not, go there next!
			for (Location location : locationANNs.get()) {
				String locationName = location.location_name();
				if (!location.location_id().equals(startingCityId)
						&& !cityPathInOrder.contains(locationName) 
						&& (!locationName.equals(cityName))) {
					// add to return val cityPathInOrder
					cityPathInOrder.add(locationName);
					// remove from notYetVisited
					notYetVisited.remove(locationName);
					// set newest city
					city = Optional.of(location);
					// break, because we recompute the ANNs each time from the newest city
					break;
				}
			}
		}
		
        if (cityPathInOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(cityPathInOrder.stream());
	}
	
	@GetMapping("/highways/from/{startlocationid}/to/{endlocationid}")
	public ResponseEntity<HighwayResponse> getCity(
			HttpServletRequest req,
            @PathVariable(value = "startlocationid") 
            String startCityId,
            @PathVariable(value = "endlocationid") 
            String endCityId) {
		
		List<Highway> highways = new ArrayList<>();
		Set<String> citiesOnRoute = new HashSet<>();
		
		Optional<Location> startCity = transportDAL.getLocation(startCityId);
		Optional<Location> endCity = transportDAL.getLocation(endCityId);
		String startName = startCity.get().location_name();
		String endName = endCity.get().location_name();

		// get highways between/near the start and end locations
		if (startCity.isPresent() && endCity.isPresent()) {
			highways = transportDAL.getHighwaysByCity(startCity.get(),endCity.get());
			
			// iterate through the highways, remove any orphans
			highways = traverse(highways,startCity.get(),endCity.get());
			
			// check for cities on the highways list
			for (Highway highway : highways) {
				CqlVectorSerializable vector =
						new CqlVectorSerializable((CqlVector<Float>) highway.highway_vector());
				// should be 2 pair of coords in each vector
				CqlVector<Float> vectorPair1 = CqlVector.builder()
						.add(vector.getValues().get(0))
						.add(vector.getValues().get(1))
						.build();
				
				CqlVector<Float> vectorPair2 = CqlVector.builder()
						.add(vector.getValues().get(2))
						.add(vector.getValues().get(3))
						.build();
	
				Optional<List<Location>> location1 = transportDAL.getANNByVector(vectorPair1, 1);
				Optional<List<Location>> location2 = transportDAL.getANNByVector(vectorPair2, 1);
	
				if (location1.get().getFirst().similarity() == 1f) {
					if (!location1.get().getFirst().location_name().equals(endName) &&
							!location1.get().getFirst().location_name().equals(startName)) {
						citiesOnRoute.add(location1.get().getFirst().location_name());
					}
				}
	
				if (location2.get().getFirst().similarity() == 1f) {
					if (!location2.get().getFirst().location_name().equals(endName) &&
							!location2.get().getFirst().location_name().equals(startName)) {
						citiesOnRoute.add(location2.get().getFirst().location_name());
					}
				}
			}
		}
		
		HighwayResponse response = new HighwayResponse(highways, citiesOnRoute);
		
		return ResponseEntity.ok(response);
	}

	private List<Highway> traverse(List<Highway> highways, Location start, Location end) {
		List<Highway> route = new ArrayList<>();
		List<String> vectorPairs = new ArrayList<>();
		
		// iterate through highways, convert highway vectors into list of string pairs
		for (Highway highway : highways) {
			CqlVectorSerializable<Float> highwayVector = new CqlVectorSerializable<Float>((CqlVector<Float>) highway.highway_vector());
			
			// split vector values into pairs
			vectorPairs.add(highwayVector.getValues().get(0) + "," + highwayVector.getValues().get(1));
			vectorPairs.add(highwayVector.getValues().get(2) + "," + highwayVector.getValues().get(3));			
		}
		
		// iterate through highways again, this time checking to see if
		// the vector pairs are unique (and not also equal to a location's vector pair)? 
		for (Highway highway : highways) {
			CqlVectorSerializable<Float> highwayVector = new CqlVectorSerializable<Float>((CqlVector<Float>) highway.highway_vector());
			String pair1 = highwayVector.getValues().get(0) + "," + highwayVector.getValues().get(1);
			String pair2 = highwayVector.getValues().get(2) + "," + highwayVector.getValues().get(3);
			
			// also need actual CqlVectors for query
			CqlVector<Float> vectorPair1 = CqlVector.builder()
					.add(highwayVector.getValues().get(0))
					.add(highwayVector.getValues().get(1))
					.build();
			CqlVector<Float> vectorPair2 = CqlVector.builder()
					.add(highwayVector.getValues().get(2))
					.add(highwayVector.getValues().get(3))
					.build();
			
			boolean remove = false;
			if (vectorPairs.indexOf(pair1) == vectorPairs.lastIndexOf(pair1)) {
				// unique!  Is it a city?
				Optional<List<Location>> location1 = transportDAL.getANNByVector(vectorPair1, 1);
				
				if (location1.get().get(0).similarity() != 1f) {
					// is not a city! - REMOVE
					remove = true;
				} else {
					// it IS a city, BUT
					// is it a start or end city?
					String startCity = start.location_vector()
							.toString().replace(" ","");
					String endCity = end.location_vector()
							.toString().replace(" ","");
					
					if (!startCity.contains(pair1) && !endCity.contains(pair1)) {
						// is NOT a start or end city! - REMOVE
						remove = true;
					}
				}
			}
			
			if (!remove &&
					vectorPairs.indexOf(pair2) == vectorPairs.lastIndexOf(pair2)) {
				// unique!  Is it a city?
				Optional<List<Location>> location2 = transportDAL.getANNByVector(vectorPair2, 1);
				
				if (location2.get().get(0).similarity() != 1f) {
					// is not a city! - REMOVE
					remove = true;
				} else {
					// it IS a city, BUT
					// is it a start or end city?
					String startCity = start.location_vector()
							.toString().replace(" ","");
					String endCity = end.location_vector()
							.toString().replace(" ","");
					
					if (!startCity.contains(pair2) && !endCity.contains(pair2)) {
						// is NOT a start or end city! - REMOVE
						remove = true;
					}
				}
			}
			
			if (!remove) {
				// highway passes all checks - ADD
				route.add(highway);
			}
		}
		
		return route;
	}
	
}
