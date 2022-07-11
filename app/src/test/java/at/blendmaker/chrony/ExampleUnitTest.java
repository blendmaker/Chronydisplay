package at.blendmaker.chrony;

import org.androidannotations.annotations.SystemService;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testStringBuffer() {
        StringBuffer str = new StringBuffer("0.91;\n\r");

        System.out.println( str.toString() );
        System.out.println( Float.valueOf( str.substring(0, str.indexOf(";") ) ).toString() );
        str.delete(0, str.indexOf(";") + 1);
        while (str.indexOf("\n") >= 0) {
            str.deleteCharAt( str.indexOf("\n") );
        }
        while (str.indexOf("\r") >= 0) {
            str.deleteCharAt( str.indexOf("\r") );
        }
        System.out.println( str.toString() );
    }

    private static Float getMps(StringBuffer s) {
        if (s.indexOf(";") >= 0) {
            Float mps = Float.valueOf( s.substring(0, s.indexOf(";") ) );
            s.delete(0, s.indexOf(";") + 1);
            if (s.indexOf("\n") >= 0) {
                s.deleteCharAt(s.indexOf("\n") + 1);
            }
            if (s.indexOf("\r") >= 0) {
                s.deleteCharAt(s.indexOf("\r") + 1);
            }
            if (mps > 0f) {
                return mps;
            }
        }
        return 0f;

    }
}