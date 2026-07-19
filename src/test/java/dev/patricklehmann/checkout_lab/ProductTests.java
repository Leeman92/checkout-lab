package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ProductTests {

    @Test
    void calculatesDerivedPriceAndAvailableStockAfterLoading()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Product product = new Product();
        product.setNetPriceInCents(1999);
        product.setTotalStock(25);
        product.setReservedStock(7);

        Method postLoad = Product.class.getDeclaredMethod("postLoad");
        postLoad.setAccessible(true);
        postLoad.invoke(product);

        assertThat(product.getNetFormattedPrice()).isEqualTo(19.99);
        assertThat(product.getAvailableStock()).isEqualTo(18);
    }
}
