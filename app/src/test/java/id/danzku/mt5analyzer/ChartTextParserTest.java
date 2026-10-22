package id.danzku.mt5analyzer;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ChartTextParserTest {
    @Test public void parsesChartNumbers() {
        Snapshot s = ChartTextParser.parse(Arrays.asList(
            new ChartTextParser.Line("XAUUSDm M5", 0.05, 0.10),
            new ChartTextParser.Line("SELL 4100.273", 0.08, 0.16),
            new ChartTextParser.Line("RSI(14) 62.36", 0.05, 0.56),
            new ChartTextParser.Line("ATR(14) 7.056", 0.05, 0.80)));
        assertEquals("XAUUSDm", s.symbol);
        assertEquals("M5", s.timeframe);
        assertEquals(4100.273d, s.price, 0.0001);
        assertEquals(62.36d, s.rsi14, 0.001);
        assertEquals(7.056d, s.atr14, 0.001);
        assertFalse(s.priceConfirmed);
    }
    @Test public void ignoresChatAboveChart() {
        Snapshot s = ChartTextParser.parse(Arrays.asList(
            new ChartTextParser.Line("RSI(14) 99.0", 0.1, 0.01),
            new ChartTextParser.Line("XAUUSDm M5", 0.1, 0.50),
            new ChartTextParser.Line("RSI(14) 18.91", 0.1, 0.82)));
        assertEquals(18.91, s.rsi14, 0.001);
    }
    @Test public void missingValuesRemainNull() {
        Snapshot s = ChartTextParser.parse(Arrays.asList(
            new ChartTextParser.Line("XAUUSDm M5", 0.02, 0.1),
            new ChartTextParser.Line("4100", 0.93, 0.4)));
        assertNull(s.price); assertNull(s.rsi14); assertNull(s.atr14);
    }
    @Test public void rejectInvalidRsi() {
        Snapshot s = ChartTextParser.parse(Arrays.asList(new ChartTextParser.Line("RSI(14) 147.25", 0.02, 0.2)));
        assertNull(s.rsi14);
    }
}
