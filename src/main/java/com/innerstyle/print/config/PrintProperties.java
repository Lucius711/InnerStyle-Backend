package com.innerstyle.print.config;

import com.innerstyle.print.entity.enums.PrintMaterial;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 3D-print pricing config (bound from {@code app.print.*}). Price is driven by the figurine
 * height in cm; {@code fee} is the fallback when a size has no explicit price.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.print")
public class PrintProperties {

    /** Fallback flat fee (VND) when a requested size isn't in {@link #prices}. */
    private BigDecimal fee = new BigDecimal("300000");

    /** Sell price (VND) keyed by figurine height in cm, e.g. {8: 649000, 12: 749000, 15: 849000}. */
    private Map<Integer, BigDecimal> prices = new LinkedHashMap<>();

    /** Surcharge (VND) added on top of the size price, keyed by plastic, e.g. {PLA: 0, PETG: 50000, RESIN: 150000}. */
    private Map<PrintMaterial, BigDecimal> materials = new LinkedHashMap<>();

    /** Whether a given material has a configured surcharge (used to validate the order request). */
    public boolean supportsMaterial(PrintMaterial material) {
        return material != null && materials.containsKey(material);
    }

    /** Total price for a size + material; callers must check {@link #supportsMaterial} first. */
    public BigDecimal priceFor(Integer sizeCm, PrintMaterial material) {
        return priceFor(sizeCm).add(materials.get(material));
    }

    /** Resolve the price for a size, falling back to the flat fee. */
    public BigDecimal priceFor(Integer sizeCm) {
        if (sizeCm != null && prices.containsKey(sizeCm)) {
            return prices.get(sizeCm);
        }
        return fee;
    }

    /** Whether a given size has a configured price (used to validate the order request). */
    public boolean supports(Integer sizeCm) {
        return sizeCm != null && prices.containsKey(sizeCm);
    }
}
