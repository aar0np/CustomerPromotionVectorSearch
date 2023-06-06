package com.datastax.customerpromotion;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import astraconnect.AstraConnection;

@RequestMapping("/promotionsvc")
@RestController
public class PromotionController {
	private CqlSession session;
	
	private record Promotion(String productid,
			String productname,
			Object vector) {
	}
	
	public PromotionController() {
		AstraConnection conn = new AstraConnection();
		session = conn.getCqlSession();
	}
	
	@GetMapping("/promoproduct/{productid}")
	public ResponseEntity<Promotion> getPromotionProduct(HttpServletRequest req,
            @PathVariable(value = "productid") 
            String productid) {

		// get original product detail
		PreparedStatement qpPrepared = session.prepare("SELECT * FROM pet_supply_vectors WHERE product_id = ?");
		BoundStatement qpBound = qpPrepared.bind(productid);
		ResultSet rs = session.execute(qpBound);
		Row product = rs.one();
				
		if (product.size() > 0) {
			// product exists, now query by its vector to get the closest product match
			Promotion originalProduct = new Promotion(productid,
					product.getString("product_name"),
					product.getCqlVector("product_vector"));

			PreparedStatement qvPrepared = session.prepare("SELECT * FROM pet_supply_vectors ORDER BY product_vector ANN OF ? LIMIT 2;");
			BoundStatement qvBound = qvPrepared.bind(originalProduct.vector);
			ResultSet rsV = session.execute(qvBound);
			List<Row> ann = rsV.all();
			List<Promotion> promoProds = new ArrayList<>();
			
			if (ann.size() > 1) {
				// only add new product to promoProds list
				for (Row promo : ann) {
					String promoProdId = promo.getString("product_id");
					
					if (!promoProdId.equals(originalProduct.productid)) {
						Promotion annPromoProd = new Promotion(promoProdId,
								promo.getString("product_name"),
								promo.getCqlVector("product_vector"));
						promoProds.add(annPromoProd);
						//once we find it, no need to check the others - break!
						break;
					}
				}
				
				if (!promoProds.isEmpty()) {
					return ResponseEntity.ok(promoProds.get(0));
				}
			}
		}
		
		return ResponseEntity.notFound().build();
	}

}
