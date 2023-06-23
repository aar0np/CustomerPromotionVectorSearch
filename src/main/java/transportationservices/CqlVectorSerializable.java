package transportationservices;

import com.datastax.oss.driver.api.core.data.CqlVector;

import java.util.ArrayList;
import java.util.List;

public class CqlVectorSerializable<T> {

    List<T> values = new ArrayList<>();

    public CqlVectorSerializable(CqlVector<T> vector) {
        vector.getValues().forEach(values::add);
    }

    /**
     * Gets values
     *
     * @return value of values
     */
    public List<T> getValues() {
        return values;
    }

    /**
     * Set value for values
     *
     * @param values
     *         new value for values
     */
    public void setValues(List<T> values) {
        this.values = values;
    }
}