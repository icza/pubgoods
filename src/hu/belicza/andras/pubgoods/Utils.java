package hu.belicza.andras.pubgoods;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TimeZone;

import javax.servlet.http.HttpServletResponse;

public class Utils {
	
	/** Google Analytics code to be included immediately before the closing <code>&lt;/head&gt;</code> tag. */
	public static final String GA_TRACKER_HTML_SCRIPT = "<script type=\"text/javascript\">"
		+ "var _gaq = _gaq || [];"
		+ "_gaq.push(['_setAccount', 'UA-4884955-26']);"
		+ "_gaq.push(['_trackPageview']);"
		+ "(function() {"
		+ "var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;"
		+ "ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';"
		+ "var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);"
		+ "})();"
		+ "</script>";
	
	/** Google Adsense code, Header ad 728x90. */
	public static final String HEADER_AD_728_90_HTML_SCRIPT = "<p class=\"ads\"><script type=\"text/javascript\">"
		+ "google_ad_client = \"ca-pub-4479321142068297\";"
		+ "google_ad_slot = \"4124263936\";"
		+ "google_ad_width = 728;"
		+ "google_ad_height = 90;"
		+ "</script>"
		+ "<script type=\"text/javascript\""
		+ "src=\"https://pagead2.googlesyndication.com/pagead/show_ads.js\">"
		+ "</script></p>";
	
	/** Digits used in the hexadecimal representation. */
	public static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	
	public static final String[] DIGEST_ALGORITHMS;
	static {
		DIGEST_ALGORITHMS = Security.getAlgorithms( "MessageDigest" ).toArray( new String[ 0 ] );
		// Rename "SHA" to "SHA-1"
		for ( int i = DIGEST_ALGORITHMS.length - 1; i >= 0; i--)
			if ( "SHA".equals( DIGEST_ALGORITHMS[ i ] ) ) {
				DIGEST_ALGORITHMS[ i ] = "SHA-1";
				break;
			}
		Arrays.sort( DIGEST_ALGORITHMS );
	}
	
	/** Pairs: [timeZoneId, displayName] */
	public static final String[][] TIME_ZONES;
	static {
		final String[] timeZoneIds = TimeZone.getAvailableIDs();
		
		TIME_ZONES = new String[ timeZoneIds.length ][ 2 ];
		for ( int i = 0; i < timeZoneIds.length; i++ ) {
			TIME_ZONES[ i ][ 0 ] = timeZoneIds[ i ];
			TIME_ZONES[ i ][ 1 ] = timeZoneIds[ i ] + " - " + TimeZone.getTimeZone( timeZoneIds[ i ] ).getDisplayName();
		}
		
		// Sort by display name
		Arrays.sort( TIME_ZONES, new Comparator< String[] >() {
			@Override
            public int compare( final String[] t1, final String[] t2 ) {
	            return t1[ 1 ].compareTo( t2[ 1 ] );
            }
		} );
	}
	
	/**
	 * Configures the response never to cache the data.
	 * @param response reference to the response
	 */
	public static void setNoCache( final HttpServletResponse response ) {
		response.setHeader    ( "Cache-Control", "no-cache" ); // For HTTP 1.1
		response.setHeader    ( "Pragma"       , "no-cache" ); // For HTTP 1.0
		response.setDateHeader( "Expires"      , 0          ); // For proxies
	}
	
	/**
	 * Encodes an input string for HTML rendering.
	 * @param input input string to be encoded
	 * @return an encoded string for HTML rendering
	 */
	public static String encodeHtmlString( String input ) {
		if ( input == null )
			return "";
		
		final StringBuilder encodedHtml = new StringBuilder();
		
		input = input.replace( "\r\n", "\n" );
		
		final int length = input.length();
		
		for ( int i = 0; i < length; i++ ) {
			final char ch = input.charAt( i );
			
			if ( ch >= 'a' && ch <='z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' )
				encodedHtml.append( ch ); // safe
			else if ( ch == '\n' )
				encodedHtml.append( '\n' );
			else if ( Character.isISOControl( ch ) )
				encodedHtml.append( "&middot;" ); // Not safe, substitute it in the output
			else
				encodedHtml.append( "&#" ).append( (int) ch ).append( ';' );
		}
		
		return encodedHtml.toString();
	}
	
	/**
	 * Input can be either an {@link InputStream} or a {@link String}.
	 * 
	 * @param algorithm
	 * @param input
	 * @return
	 * @throws Exception
	 */
	public static String calcDigest( final String algorithm, final Object input ) throws Exception {
		final MessageDigest md     = MessageDigest.getInstance( algorithm );
		final byte[]        buffer = new byte[ 8*1024 ];
		
		if ( input instanceof String )
			md.update( ( (String) input ).getBytes( "UTF-8" ) );
		else
    		if ( input instanceof InputStream ) {
    			final InputStream inputStream = (InputStream) input;
        		int bytesRead;
        		while ( ( bytesRead = inputStream.read( buffer ) ) > 0 )
        			md.update( buffer, 0, bytesRead );
    		}
		
		return convertToHexString( md.digest() );
	}
	
	/**
	 * Converts the specified data to hex string.
	 * @param data data to be converted
	 * @return the specified data converted to hex string
	 */
	public static String convertToHexString( final byte[] data ) {
		final StringBuilder hexBuilder = new StringBuilder( data.length << 1 );
		
		for ( final byte b : data )
			hexBuilder.append( HEX_DIGITS[ ( b & 0xff ) >> 4 ] ).append( HEX_DIGITS[ b & 0x0f ] );
		
		return hexBuilder.toString();
	}
	
}
