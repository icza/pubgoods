package hu.belicza.andras.pubgoods;

import static hu.belicza.andras.pubgoods.Utils.DIGEST_ALGORITHMS;
import static hu.belicza.andras.pubgoods.Utils.GA_TRACKER_HTML_SCRIPT;
import static hu.belicza.andras.pubgoods.Utils.HEADER_AD_728_90_HTML_SCRIPT;
import static hu.belicza.andras.pubgoods.Utils.TIME_ZONES;
import static hu.belicza.andras.pubgoods.Utils.encodeHtmlString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;

import com.google.appengine.api.images.Image;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.Transform;
import com.google.appengine.api.images.Image.Format;
import com.google.appengine.api.images.ImagesService.OutputEncoding;

/*
 * Ideas, TODO's:
 *  	-"+1" button to the header, center
 *  	-Detect client's time zone...
 *  	-Image page: mix images (draw image on another image)
 * 		-File HEX viewer
 * 		-Execution time to the footer
 * 		-Email image to the footer as contact; maybe a contact service/page which sends email...
 * 		-Subscribe to newsletter: send email notification about updates (new services, new options)
 * 		-"Add target time zone to selection:" function
 * 		-Color chooser/table
 */

@SuppressWarnings( "serial" )
public class PubGoodsServlet extends HttpServlet {
	
	private static final Logger LOGGER = Logger.getLogger( PubGoodsServlet.class.getName() );
	
	private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat( "yyyy-MM-dd_HH-mm-ss" );
	
	private static final String ATTR_ERROR_MESSAGE = "errorMessage";
	
	private enum Page {
		EMPTY       ( "empty"      , false, null         , null                               , null ),
		INDEX       ( "index.html" , true , "Index"      , "Public Services Index Page"       , "Public Services is a web site to provide a set of useful tools for free.<br/><i>\"There is no charge for awesomeness!\"</i>" ),
		LOCATION    ( "location"   , true , "Location"   , "Your Location Info"               , "This page displays your location related info which can be derived from your request such as your public IP address and your location.<br/>Answers questions like <i>\"What is my IP address?\"</i>, <i>\"What is my country and city?\"</i>, <i>\"Where am I on the map?\"</i>." ),
		IMAGE       ( "image"      , true , "Image"      , "Image Conversion"                 , "This page allows you to convert images from one type to another, and perform basic operations on the converted image. The output image is automatically compressed.<br/><br/>Supported image transformations: Crop, Resize, Rotate, Flip Horizontally, Flip Vertically, Optimize (enhance dark and bright colors and adjust color and contrast)" ),
		IMAGE_RESULT( "imageresult", false, null         , null                               , null ),
		TIMEZONE    ( "timezone"   , true , "Time Zone"  , "Time Zone Converter and Countdown", "This page allows you to convert times from one time zone to others, and display the elapsed time since or the remaining time to the reference time.<br/><i>Tip: You can select multiple target time zones.</i><br/><i>Tip: After a conversion, you can copy and paste the URL to display the same conversion (including source time and selected time zones).</i>" ),
		DIGEST      ( "digest"     , true , "Digest/Hash", "Digest / Hash Calculation"        , "This page allows you to calculate digest / hash / checksum values of files or your custom text (entered by you)." ),
		BASE64      ( "base64"     , true , "Base64"     , "Base64 Encoder"                   , "This page allows you to convert files or your custom text (entered by you) to Base64 string.<br/>Max allowed input size: 32 MB" ),
		BROWSER     ( "browser"    , true , "Browser"    , "Your Browser Info"                , "This page displays info about your Browser and Operating System." ),
		REQUEST     ( "request"    , true , "Request"    , "Your Request Info"                , "This page displays info about your web request to this server. Useful for debuggin purposes or to see what is under the hood.<br/>Displayed info include: request headers, basic request properties, request parameters." ),
		DONATE      ( "donate"     , true , "Donate"     , "Donate"                           , "On this page you can send me a donation to support me or to show your appreciation for these free services." ),
		ERROR       ( "error"      , false, "Error"      , "Error!"                           , "You see this page because an error occured during serving your request." );
		
		public final String  path;
		public final boolean listable;
		public final String  name;
		public final String  title;
		public final String  description;
		
		private Page( final String path, final boolean listable, final String name, final String title, final String  description ) {
			this.path        = path;
			this.listable    = listable;
			this.name        = name;
			this.title       = title;
			
			// Produce dynamic descriptions:
			if ( "digest".equals( path ) ) {
				final StringBuilder descBuilder = new StringBuilder( description );
				descBuilder.append( "<br/>" );
				for ( int i = 0; i < DIGEST_ALGORITHMS.length; i++ ) {
					if ( i == 0 )
						descBuilder.append( "<br/>Supported algorithms: " );
					else
						descBuilder.append( ", " );
					descBuilder.append( DIGEST_ALGORITHMS[ i ] );
				}
				descBuilder.append( "<br/>Max allowed input size: 32 MB" );
				this.description = descBuilder.toString();
			}
			else if ( "image".equals( path ) ) {
				final StringBuilder descBuilder = new StringBuilder( description );
				final Format[] iamgeFormats = Image.Format.values();
				for ( int i = 0; i < iamgeFormats.length; i++ ) {
					if ( i == 0 )
						descBuilder.append( "<br/>Supported input image formats: " );
					else
						descBuilder.append( ", " );
					descBuilder.append( iamgeFormats[ i ] );
				}
				final OutputEncoding[] outputEncodings = ImagesService.OutputEncoding.values();
				for ( int i = 0; i < outputEncodings.length; i++ ) {
					if ( i == 0 )
						descBuilder.append( "<br/>Supported output image formats: " );
					else
						descBuilder.append( ", " );
					descBuilder.append( outputEncodings[ i ] );
				}
				descBuilder.append( "<br/>Max allowed input and output image size: 32 MB or 50 megapixels" );
				this.description = descBuilder.toString();
			}
			else
				this.description = description; // Static description
		}
	}
	
	private static final Page[] PAGES = Page.values();
	
	public void doPost( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		doGet( request, response );
	}
	
	public void doGet( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		Page page = null;
		
		final String pathInfo = request.getPathInfo();
		if ( pathInfo == null || pathInfo.length() == 1 )
			page = Page.INDEX;
		else {
			final String path = pathInfo.substring( 1 );
			
			for ( final Page page_ : PAGES )
				if ( page_.path.equals( path ) ) {
					page = page_;
					break;
				}
			
			if ( page == null ) {
				response.sendError( HttpServletResponse.SC_NOT_FOUND ); // This will trigger a forward() call to "/error?code=404" (defined in web.xml)
				return;
			}
		}
		
		if ( page != Page.EMPTY )
			LOGGER.fine( "Location: " + request.getHeader( "X-AppEngine-Country" ) + ";" + request.getHeader( "X-AppEngine-Region" ) + ";" + request.getHeader( "X-AppEngine-City" ) + ";" + request.getHeader( "X-AppEngine-CityLatLong" ) );
		
		// Special pages with unique behavior/response (no header or footer must be rendered for these)
		switch ( page ) {
		case EMPTY        : return; // Nothing to do
		case IMAGE_RESULT : serveImageResult( request, response ); return;
		}
		
		renderHeader( request, response, page );
		
		switch ( page ) {
		case INDEX    : serveIndex   ( request, response ); break;
		case LOCATION : serveLocation( request, response ); break;
		case IMAGE    : serveImage   ( request, response ); break;
		case TIMEZONE : serveTimeZone( request, response ); break;
		case DIGEST   : serveDigest  ( request, response ); break;
		case BASE64   : serveBase64  ( request, response ); break;
		case BROWSER  : serveBrowser ( request, response ); break;
		case REQUEST  : serveRequest ( request, response ); break;
		case DONATE   : serveDonate  ( request, response ); break;
		case ERROR    : serveError   ( request, response ); break;
		}
		
		renderFooter( request, response );
	}
	
	private static void serveIndex( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		out.println( "<p><b>Available public services:</b></p><ul>" );
		for ( final Page page : PAGES ) {
			if ( !page.listable )
				continue;
			out.print( "<li><a href=\"" );
			out.print( page.path );
			out.print( "\">" );
			out.print( page.title );
			out.println( "</a></li>" );
		}
		out.println( "</ul><i>More awesome services are on the way... ;)</i>" );
	}
	
	private static void serveLocation( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		// TODO resolve country code to name
		final PrintWriter out = response.getWriter();
		out.println( "<h3>Your IP and location details:</h3>" );
		out.print  ( "<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\">" );
		out.println( "<tr><th>Public IP<td>" + request.getRemoteAddr() );
		out.println( "<tr><th>Country code<td>" + encodeHtmlString( request.getHeader( "X-AppEngine-Country" ) ) );
		out.println( "<tr><th>Region code<td>" + encodeHtmlString( request.getHeader( "X-AppEngine-Region" ) ) );
		out.println( "<tr><th>City<td>" + encodeHtmlString( request.getHeader( "X-AppEngine-City" ) ) );
		final String cityLatLong = request.getHeader( "X-AppEngine-CityLatLong" );
		out.println( "<tr><th>City coordinates<td>" + ( cityLatLong == null ? "" : cityLatLong ) );
		out.println( "\n</table>" );
		out.println( "<h3>Here is a map of your location:</h3>" );
		if ( cityLatLong == null )
			out.println( "No map info available :(" );
		else
			out.println( "<img src=\"http" + ( request.isSecure() ? "s" : "" ) + "://maps.googleapis.com/maps/api/staticmap?markers=" + cityLatLong + "&zoom=12&size=500x500&sensor=false\">" );
	}
	
	private static void serveImage( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		
		// Render form
		out.println( "<form action=\"" + Page.IMAGE_RESULT.path + "\" method=\"POST\" enctype=\"multipart/form-data\" target=\"_blank\">" );
		out.print  ( "<table><tr><td style=\"white-space:nowrap\">Choose an image:<td><input type=\"file\" name=\"fileName\" style=\"width:100%\"><td class=\"explanation\">Supported input image formats: " );
		final Format[] imageFormats = Image.Format.values();
		for ( int i = 0; i < imageFormats.length; i++ ) {
			if ( i > 0 )
				out.print( ", " );
			out.print( imageFormats[ i ] );
		}
		out.println( "<tr><td>Crop image:<td style=\"white-space:nowrap\">Left X:<input type=\"text\" name=\"leftX\" value=\"0.0\" style=\"width:40px\"> Top Y:<input type=\"text\" name=\"topY\" value=\"0.0\" style=\"width:40px\"> Right X:<input type=\"text\" name=\"rightX\" value=\"1.0\" style=\"width:40px\"> Bottom Y:<input type=\"text\" name=\"bottomY\" value=\"1.0\" style=\"width:40px\"><td class=\"explanation\">The crop window, all values must be in the range of 0..1, inclusive." );
		out.println( "<tr><td>Resize image:<td style=\"white-space:nowrap\">Width pixels:<input type=\"text\" name=\"width\" style=\"width:50px\"> Height pixels:<input type=\"text\" name=\"height\" style=\"width:50px\"> Allow stretch:<input type=\"checkbox\" name=\"allowStretch\" value=\"allowStretch\"><td class=\"explanation\">Allow stretch: allow the image to be resized ignoring the aspect ratio." );
		out.println( "<tr><td>Rotate image:<td><select name=\"rotate\" style=\"width:100%\"><option value=\"0\">Do Not Rotate</option><option value=\"90\">90&deg;</option><option value=\"180\">180&deg;</option><option value=\"270\">270&deg;</option></select><td class=\"explanation\">Rotation degree is clock-wise." );
		out.println( "<tr><td style=\"white-space:nowrap\">Flip Horizontally:<td><input type=\"checkbox\" name=\"flipHor\" value=\"flipHor\">" );
		out.println( "<tr><td style=\"white-space:nowrap\">Flip Vertically:<td><input type=\"checkbox\" name=\"flipVer\" value=\"flipVer\">" );
		out.println( "<tr><td>Optimize:<td><input type=\"checkbox\" name=\"optimize\" value=\"optimize\"><td class=\"explanation\">Optimization enhances dark and bright colors and adjusts color and contrast." );
		out.print  ( "<tr><td>Output format:<td><select name=\"outFormat\" style=\"width:100%\">" );
		for ( final OutputEncoding outputEncoding : OutputEncoding.values() )
		 	out.print( "<option value=\"" + outputEncoding + "\"" + ( outputEncoding == OutputEncoding.JPEG ? " selected>" : ">" )+ outputEncoding + "</option>" );
		out.println( "</select>" );
		out.println( "<tr><td colspan=\"2\" style=\"padding-top:10px\"><input type=\"submit\" name=\"submitView\" value=\"View Image in New Tab\" onclick=\"if (this.form.fileName.value!='') {return true;}alert('Please choose an image!');return false;\"><input type=\"submit\" name=\"submitDownload\" value=\"Download Image\" onclick=\"if (this.form.fileName.value!='') {return true;}alert('Please choose an image!');return false;\">" );
		out.println( "</table></form>" );
	}
	
	private static void serveImageResult( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		String message;
		if ( !ServletFileUpload.isMultipartContent( request ) ) {
			LOGGER.severe( message = "Missing parameters!" );
			request.setAttribute( ATTR_ERROR_MESSAGE, message );
			response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
			return;
		}
		
		final Map< String, String > paramMap = new HashMap< String, String >();
		
		// Process uploaded file
		Image image     = null;
		String fileName = null;
		
		message = "Not a valid image file!";
		final ServletFileUpload uploadProcessor = new ServletFileUpload();
		try {
			final FileItemIterator itemIterator = uploadProcessor.getItemIterator( request );
			while ( itemIterator.hasNext() ) {
				final FileItemStream item       = itemIterator.next();
				final InputStream    itemStream = item.openStream();
				if ( item.isFormField() )
					paramMap.put( item.getFieldName(), Streams.asString( itemStream ) );
				else {
					if ( ( fileName = item.getName() ).length() > 0 ) {
						message = '"' + fileName + "\" is not a valid image file!";
						final ByteArrayOutputStream bout = new ByteArrayOutputStream();
						final byte[] buffer = new byte[ 8*1024 ];
						int bytesRead;
						while ( ( bytesRead = itemStream.read( buffer ) ) > 0 )
							bout.write( buffer, 0, bytesRead );
						final Image image_ = ImagesServiceFactory.makeImage( bout.toByteArray() );
						// Test if image is valid:
						image_.getFormat();
						// No exception means image OK
						image = image_;
					}
				}
			}
		} catch ( final Exception e ) {
			LOGGER.log( Level.SEVERE, message, e );
			request.setAttribute( ATTR_ERROR_MESSAGE, message );
			response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
			return;
		}
		
		LOGGER.fine( "Params: " + paramMap.toString() );
		
		if ( image == null ) {
			LOGGER.severe( message = "Missing image file!" );
			request.setAttribute( ATTR_ERROR_MESSAGE, message );
			response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
			return;
		}
		
		OutputEncoding outputEncoding;
		try {
			outputEncoding = OutputEncoding.valueOf( paramMap.get( "outFormat" ) );
		} catch ( final Exception e ) {
			LOGGER.log( Level.SEVERE, message = "Unsupported output format: " + paramMap.get( "outFormat" ), e );
			request.setAttribute( ATTR_ERROR_MESSAGE, message );
			response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
			return;
		}
		
		// Transform image
		final ImagesService imagesService = ImagesServiceFactory.getImagesService();
		final List< Transform > transformList = new ArrayList< Transform >();
		if ( !"0.0".equals( paramMap.get( "leftX" ) ) || !"0.0".equals( paramMap.get( "topY" ) ) || !"1.0".equals( paramMap.get( "rightX" ) ) || !"1.0".equals( paramMap.get( "bottomY" ) ) ) {
			try {
				transformList.add( ImagesServiceFactory.makeCrop( Double.parseDouble( paramMap.get( "leftX" ) ), Double.parseDouble( paramMap.get( "topY" ) ), Double.parseDouble( paramMap.get( "rightX" ) ), Double.parseDouble( paramMap.get( "bottomY" ) ) ) );
			} catch ( final Exception e ) {
				LOGGER.log( Level.SEVERE, message = "Invalid Crop parameters: " + paramMap.get( "leftX" ) + ";" + paramMap.get( "topY" ) + ";" + paramMap.get( "rightX" ) + paramMap.get( "bottomY" ), e );
				request.setAttribute( ATTR_ERROR_MESSAGE, message );
				response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
				return;
			}
		}
		if ( !"".equals( paramMap.get( "width" ) ) || !"".equals( paramMap.get( "height" ) ) ) {
			try {
				transformList.add( ImagesServiceFactory.makeResize( Integer.parseInt( paramMap.get( "width" ) ), Integer.parseInt( paramMap.get( "height" ) ), paramMap.get( "allowStretch" ) != null ) );
			} catch ( final Exception e ) {
				LOGGER.log( Level.SEVERE, message = "Invalid Resize parameters: width: " + paramMap.get( "width" ) + "; height: " + paramMap.get( "height" ), e );
				request.setAttribute( ATTR_ERROR_MESSAGE, message );
				response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
				return;
			}
		}
		if ( !"0".equals( paramMap.get( "rotate" ) ) ) { 
			try {
				transformList.add( ImagesServiceFactory.makeRotate( Integer.parseInt( paramMap.get( "rotate" ) ) ) );
			} catch ( final Exception e ) {
				LOGGER.log( Level.SEVERE, message = "Invalid Rotate parameter: " + paramMap.get( "rotate" ), e );
				request.setAttribute( ATTR_ERROR_MESSAGE, message );
				response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
				return;
			}
		}
		if ( paramMap.get( "flipHor" ) != null ) 
			transformList.add( ImagesServiceFactory.makeHorizontalFlip() );
		if ( paramMap.get( "flipVer" ) != null ) 
			transformList.add( ImagesServiceFactory.makeVerticalFlip() );
		if ( paramMap.get( "optimize" ) != null ) 
			transformList.add( ImagesServiceFactory.makeImFeelingLucky() );
		if ( transformList.isEmpty() )
			transformList.add( ImagesServiceFactory.makeRotate( 0 ) );
		
		try {
			final Transform transform = ImagesServiceFactory.makeCompositeTransform( transformList );
			imagesService.applyTransform( transform, image, outputEncoding );
		} catch ( final Exception e ) {
			LOGGER.log( Level.SEVERE, message = "Internal error!", e );
			response.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message );
			return;
		}
		
		// Send image
		final Image.Format outFormat = image.getFormat();
		final String mimeType  = outFormat == Image.Format.JPEG ? "image/jpeg" : outFormat == Image.Format.PNG ? "image/png" : outFormat == Image.Format.WEBP ? "image/webp" : null;
		final String extension = outFormat == Image.Format.JPEG ? "jpg"        : outFormat == Image.Format.PNG ? "png"       : outFormat == Image.Format.WEBP ? "webp"       : null;
		if ( mimeType == null ) {
			LOGGER.severe( message = "Unsupported output format!" );
			request.setAttribute( ATTR_ERROR_MESSAGE, message );
			response.sendError( HttpServletResponse.SC_BAD_REQUEST, message );
			return;
		}
		
		Utils.setNoCache( response );
		if ( paramMap.get( "submitDownload" ) != null )
			response.setHeader( "Content-Disposition", "attachment;filename=pubgoods_image_" + TIMESTAMP_FORMAT.format( new Date() ) + "." + extension );
		response.setContentType( mimeType );
		response.getOutputStream().write( image.getImageData() );
	}
	
	private static void serveTimeZone( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		
		String   sourceTimeZone   = request.getParameter( "s" );
		String   sourceTimeString = request.getParameter( "d" );
		String[] targetTimeZones  = request.getParameterValues( "t" );
		
		if ( sourceTimeZone != null && sourceTimeString != null && targetTimeZones != null ) {
			// Process submitted info, render output message
			final DateFormat dateTimeFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
			dateTimeFormat.setTimeZone( TimeZone.getTimeZone( sourceTimeZone ) );
			try {
				final Date sourceTime = dateTimeFormat.parse( sourceTimeString );
				// Counter
				out.println( "<p class=\"message\" id=\"remTime\" style=\"font-weight:bold\"></p>" );
				final long remainingTime = ( sourceTime.getTime() - new Date().getTime() ) / 1000;
				out.println( "<script>var remTimeSec = " + remainingTime + ";</script>" );
				out.println( "<script>function updateRemTime(){var txt='';"
						+ "var sec=Math.abs(remTimeSec);var days=Math.floor(sec/86400);if(days>0)txt+=days+(days==1?' day ':' days ');"
						+ "sec%=86400;var hours=Math.floor(sec/3600);if(hours>0||txt!='')txt+=hours+(hours==1?' hour ':' hours ');"
						+ "sec%=3600;var mins=Math.floor(sec/60);if(mins>0||txt!='')txt+=mins+' min ';"
						+ "sec%=60;txt+=sec+' sec';txt='<span style=\"padding-left:30px;font-size:140%\">'+txt+'</span>';document.getElementById('remTime').innerHTML=(remTimeSec--<0?'Elapsed time since: ':'Remaining time: ')+txt;"
						+ "setTimeout('updateRemTime()',1000);}updateRemTime();</script>" );
				// Converted times
				out.println( "<p class=\"message\"><table><tr><td style=\"padding-right:20px\">Source time:<td style=\"padding-right:20px\">" + dateTimeFormat.format( sourceTime ) + "<td style=\"padding-right:20px\">" + dateTimeFormat.getTimeZone().getID() + "<td>" + dateTimeFormat.getTimeZone().getDisplayName() );
				for ( final String targetTimeZone : targetTimeZones ) {
					dateTimeFormat.setTimeZone( TimeZone.getTimeZone( targetTimeZone ) );
					out.println( "<tr><td>Target time:<td>" + dateTimeFormat.format( sourceTime ) + "<td style=\"padding-right:20px\">" + dateTimeFormat.getTimeZone().getID() + "<td>" + dateTimeFormat.getTimeZone().getDisplayName() );
				}
				out.println( "</table></p>" );
			} catch ( ParseException pe ) {
				LOGGER.log( Level.WARNING, "", pe );
				out.println( "<p class=\"error\">Bad request: could not parse source time!</p>" );
				sourceTimeString = null;
			}
		}
		
		// Default values
		if ( sourceTimeZone == null )
			sourceTimeZone = TimeZone.getDefault().getID();
		if ( sourceTimeString == null ) {
			final DateFormat dateTimeFormat = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
			dateTimeFormat.setTimeZone( TimeZone.getTimeZone( sourceTimeZone ) );
			sourceTimeString = dateTimeFormat.format( new Date() );
		}
		if ( targetTimeZones == null )
			targetTimeZones = new String[] { "GMT", "PST", "EST", "CET" };
		
		// Render form
		out.println( "<form action=\"" + Page.TIMEZONE.path + "\" method=\"GET\"><table>" );
		out.println( "<tr><td style=\"white-space:nowrap\">Source time zone:<td><select name=\"s\" style=\"width:100%\">" );
		for ( final String[] timeZone : TIME_ZONES )
			out.println( "<option value=\"" + timeZone[ 0 ] + "\"" + ( timeZone[ 0 ].equals( sourceTimeZone ) ? " selected" : "" ) + ">" + timeZone[ 1 ] + "</option>" );
		out.println( "</select><script>if (navigator.appName==\"Microsoft Internet Explorer\") document.getElementsByName(\"s\")[0].style.width=\"520px\";</script>" );
		out.println( "<tr><td>Source time:<td><input type=\"text\" name=\"d\" value=\"" + encodeHtmlString( sourceTimeString ) + "\" style=\"width:100%\">" );
		out.println( "<tr><td style=\"white-space:nowrap\">Target time zones:<td><select name=\"t\" multiple size=\"25\" style=\"width:100%;font-size:x-small\">" );
		for ( final String[] timeZone : TIME_ZONES ) {
    		boolean selected = false;
			for ( final String targetTimeZone : targetTimeZones )
				if ( timeZone[ 0 ].equals( targetTimeZone ) ) {
					selected = true;
					break;
				}
			out.println( "<option value=\"" + timeZone[ 0 ] + "\"" + ( selected ? " selected" : "" ) + ">" + timeZone[ 1 ] + "</option>" );
		}
		out.println( "</select>" );
		out.println( "<tr><td colspan=\"2\"><input type=\"submit\" value=\"Convert Time\">" );
		out.println( "</table></form>" );
	}
	
	private static void serveDigest( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		
		final Map< String, String > paramMap = new HashMap< String, String >();
		
		if ( ServletFileUpload.isMultipartContent( request ) ) {
			// Process uploaded file
			final ServletFileUpload uploadProcessor = new ServletFileUpload();
			try {
				final FileItemIterator itemIterator = uploadProcessor.getItemIterator( request );
				while ( itemIterator.hasNext() ) {
					final FileItemStream item       = itemIterator.next();
					final InputStream    itemStream = item.openStream();
					if ( item.isFormField() ) {
						final String value = Streams.asString( itemStream );
						paramMap.put( item.getFieldName(), value );
						if ( "customText".equals( item.getFieldName() ) && value.length() > 0 ) {
							final String digest = Utils.calcDigest( paramMap.get( "algorithm" ), value );
							out.println( "<p class=\"message\">Calculated " + paramMap.get( "algorithm" ) + " digest for your text: <code>" + digest + "</code></p>" );
						}
					}
					else {
						if ( item.getName().length() > 0 ) {
							final String digest = Utils.calcDigest( paramMap.get( "algorithm" ), itemStream );
							out.println( "<p class=\"message\">Calculated " + paramMap.get( "algorithm" ) + " digest for \"" + encodeHtmlString( item.getName() )+ "\": <code>" + digest + "</code></p>" );
						}
					}
				}
			} catch ( final Exception e ) {
				LOGGER.log( Level.SEVERE, "", e );
				out.println( "<p class=\"error\">Bad request: could not calculate file digest!</p>" );
			}
		}
		
		if ( paramMap.get( "algorithm" ) != null )
			LOGGER.fine( "Algorithm: " + paramMap.get( "algorithm" ) + ( paramMap.get( "customText" ) == null ? "" : "; customText" ) );
		
		// Render form
		out.println( "<form action=\"" + Page.DIGEST.path + "\" method=\"POST\" enctype=\"multipart/form-data\"><table style=\"width:500px\">" );
		out.println( "<tr><td style=\"white-space:nowrap\">Choose an algorithm:<td style=\"width:100%\"><select name=\"algorithm\" style=\"width:100%\">" );
		final String selectedAlgorithm = paramMap.get( "algorithm"  ) == null ? "MD5" : paramMap.get( "algorithm"  );
		final String customText        = paramMap.get( "customText" ) == null ? ""    : paramMap.get( "customText" );
		for ( String algorithm : DIGEST_ALGORITHMS )
			out.println( "<option value=\"" + algorithm + "\"" + ( selectedAlgorithm.equals( algorithm ) ? " selected" : "" ) + ">" + algorithm + "</option>" );
		out.println( "</select><tr><td>Choose a file:<td style=\"width:100%\"><input type=\"file\" name=\"fileName\" style=\"width:100%\">" );
		out.print  ( "<tr><td>Or enter text:<td style=\"width:100%\"><textarea name=\"customText\" rows=\"6\" style=\"width:100%\">" );
		out.print  ( encodeHtmlString( customText ) );
		out.println( "</textarea>" );
		out.println( "<tr><td colspan=\"2\"><input type=\"submit\" name=\"submitBtn\" value=\"Calculate Digest\""
			+ " onclick=\"if (this.form.fileName.value!=''||this.form.customText.value!='') {this.form.submitBtn.value='Sending '+(this.form.fileName.value==''?'text':'file')+', please wait...';this.form.submitBtn.disabled=true;this.form.submit();return true;}alert('Please choose a file or enter text!');return false;\">" );
		out.println( "</table></form>" );
	}
	
	private static void serveBase64( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		
		final Map< String, String > paramMap = new HashMap< String, String >();
		
		if ( ServletFileUpload.isMultipartContent( request ) ) {
			// Process uploaded file
			final ServletFileUpload uploadProcessor = new ServletFileUpload();
			try {
				final FileItemIterator itemIterator = uploadProcessor.getItemIterator( request );
				while ( itemIterator.hasNext() ) {
					final FileItemStream item       = itemIterator.next();
					final InputStream    itemStream = item.openStream();
					if ( item.isFormField() ) {
						final String value = Streams.asString( itemStream );
						paramMap.put( item.getFieldName(), value );
						if ( "customText".equals( item.getFieldName() ) && value.length() > 0 ) {
							final String base64 = javax.xml.bind.DatatypeConverter.printBase64Binary( value.getBytes( "UTF-8" ) );
							out.print( "<p class=\"message\">Base64 encoded text of your input:<br/><textarea rows=\"6\" style=\"vertical-align:middle;width:600px\">" );
							out.print( base64 );
							out.println( "</textarea></p>" );
						}
					}
					else {
						if ( item.getName().length() > 0 ) {
							final ByteArrayOutputStream bout = new ByteArrayOutputStream();
							final byte[] buffer = new byte[ 8*1024 ];
							int bytesRead;
							while ( ( bytesRead = itemStream.read( buffer ) ) > 0 )
								bout.write( buffer, 0, bytesRead );
							final String base64 = javax.xml.bind.DatatypeConverter.printBase64Binary( bout.toByteArray() );
							out.print( "<p class=\"message\">Base64 encoded text of file \"" + encodeHtmlString( item.getName() )+ "\":<br/><textarea rows=\"6\" style=\"vertical-align:middle;width:600px\">" );
							out.print( base64 );
							out.println( "</textarea></p>" );
						}
					}
				}
			} catch ( final Exception e ) {
				LOGGER.log( Level.SEVERE, "", e );
				out.println( "<p class=\"error\">Bad request: could not convert data to Base64!</p>" );
			}
		}
		
		// Render form
		out.println( "<form action=\"" + Page.BASE64.path + "\" method=\"POST\" enctype=\"multipart/form-data\"><table style=\"width:500px\">" );
		final String customText = paramMap.get( "customText" ) == null ? "" : paramMap.get( "customText" );
		if ( !customText.isEmpty() )
			LOGGER.fine( "customText" );
		out.println( "<tr><td style=\"white-space:nowrap\">Choose a file:<td style=\"width:100%\"><input type=\"file\" name=\"fileName\" style=\"width:100%\">" );
		out.print  ( "<tr><td>Or enter text:<td style=\"width:100%\"><textarea name=\"customText\" rows=\"6\" style=\"width:100%\">" );
		out.print  ( encodeHtmlString( customText ) );
		out.println( "</textarea>" );
		out.println( "<tr><td colspan=\"2\"><input type=\"submit\" name=\"submitBtn\" value=\"Convert to Base64\""
			+ " onclick=\"if (this.form.fileName.value!=''||this.form.customText.value!='') {this.form.submitBtn.value='Sending '+(this.form.fileName.value==''?'text':'file')+', please wait...';this.form.submitBtn.disabled=true;this.form.submit();return true;}alert('Please choose a file or enter text!');return false;\">" );
		out.println( "</table></form>" );
	}
	
	private static void serveBrowser( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		out.println( "<div id=\"browserInfo\"/><script>txt = \"<table border='1' cellpadding='3' cellspacing='0'>\";" );
		out.println( "txt += \"<tr><th>Browser code name:<td>\"+navigator.appCodeName;" );
		out.println( "txt += \"<tr><th>Browser name:<td>\"+navigator.appName;" );
		out.println( "txt += \"<tr><th>Browser version:<td>\"+navigator.appVersion;" );
		out.println( "txt += \"<tr><th>Cookies enabled:<td>\"+(navigator.cookieEnabled?'Yes':'No');" );
		out.println( "txt += \"<tr><th>Platform:<td>\"+navigator.platform;" );
		out.println( "txt += \"<tr><th>User-agent header:<td>\"+navigator.userAgent;" );
		out.println( "var hasFlash=false;try{var fo=new ActiveXObject('ShockwaveFlash.ShockwaveFlash');if(fo)hasFlash=true;}catch(e){if(navigator.mimeTypes[\"application/x-shockwave-flash\"]!= undefined)hasFlash=true;}" );
		out.println( "txt += \"<tr><th>Flash plugin installed:<td>\"+(hasFlash?'Yes':'No');" );
		out.println( "if (navigator.javaEnabled) txt += \"<tr><th>Java enabled:<td>\"+(navigator.javaEnabled()?'Yes':'No');" );
		out.println( "if (navigator.taintEnabled) txt += \"<tr><th>Data tainting enabled:<td>\"+(navigator.taintEnabled()?'Yes':'No');" );
		out.println( "txt += \"</table>\";document.getElementById(\"browserInfo\").innerHTML=txt;</script>" );
	}
	
	private static void serveRequest( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		
		out.println( "<h3>Request headers:</h3>" );
		out.print( "<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\">" );
		for ( @SuppressWarnings( "unchecked" ) final Enumeration< String > headerNames = request.getHeaderNames(); headerNames.hasMoreElements(); ) {
			final String headerName = headerNames.nextElement();
			if ( headerName.startsWith( "X-AppEngine" ) )
				continue; // Do not show AppEngine appended headers...
			out.print( "\n<tr><th>" + encodeHtmlString( headerName ) + "<td>" );
			boolean first = true;
			for ( @SuppressWarnings( "unchecked" ) final Enumeration< String > headerValues = request.getHeaders( headerName ); headerValues.hasMoreElements(); ) {
				if ( first )
					first = false;
				else
					out.print( "<br>" );
				out.print( encodeHtmlString( headerValues.nextElement() ) );
			}
		}
		out.println( "\n</table>" );
		
		out.println( "<h3>Request properties:</h3>" );
		out.println( "<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\">" );
		out.println( "\n<tr><th>Method<td>" + request.getMethod() );
		if ( request.getContentType() != null )
			out.println( "\n<tr><th>Content type<td>" + encodeHtmlString( request.getContentType() ) );
		if ( request.getContentLength() >= 0 )
			out.println( "\n<tr><th>Content length<td>" + request.getContentLength() );
		if ( request.getAuthType() != null )
			out.println( "\n<tr><th>Authentication type<td>" + encodeHtmlString( request.getAuthType() ) );
		if ( request.getCharacterEncoding() != null )
			out.println( "\n<tr><th>Character encoding<td>" + encodeHtmlString( request.getCharacterEncoding() ) );
		out.println( "\n<tr><th>Remote host<td>" + encodeHtmlString( request.getRemoteAddr() ) );
		out.println( "\n<tr><th>Remote address<td>" + request.getRemoteAddr() );
		out.println( "\n<tr><th>Remote port<td>" + request.getRemotePort() );
		if ( request.getRemoteUser() != null )
			out.println( "\n<tr><th>Remote user<td>" + encodeHtmlString( request.getRemoteUser() ) );
		out.println( "\n</table>" );
		
		out.println( "<h3>Request parameters:</h3>" );
		out.println( "<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\">" );
		for ( @SuppressWarnings( "unchecked" ) final Enumeration< String > paramNames = request.getParameterNames(); paramNames.hasMoreElements(); ) {
			final String paramName = paramNames.nextElement();
			out.print( "\n<tr><th>" + encodeHtmlString( paramName ) + "<td>" );
			boolean first = true;
			for ( final String paramValue : request.getParameterValues( paramName ) ) {
				if ( first )
					first = false;
				else
					out.print( "<br>" );
				out.print( encodeHtmlString( paramValue ) );
			}
		}
		out.println( "\n</table>" );
	}
	
	private static void serveDonate( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		out.println( "<p>I do my best to provide up-to-date, free of charge, easy-to-use, professional services and tools for everyone.</p>" );
		out.println( "<p>If you'd like to see Public Services to develop further, to remain free and be always available, or if you want to support me or show me your appreciation, please donate any amount through PayPal with the following link:</p>" );
		out.println( "<a href=\"https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=RTRB3YTNCEMUW&lc=US&item_name=Public%20Services&item_number=Public%20Services&currency_code=USD&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted\" target=\"_blank\"><img src=\"https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif\" border=\"0\"></a>" ); 
		out.println( "<p>Any amount is appreciated. Thank You for your donation!</p>" );
	}
	
	private static void serveError( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final String codeString = request.getParameter( "code" );
		int code;
		try {
			code = Integer.parseInt( codeString );
		} catch ( final Exception e ) {
			code = HttpServletResponse.SC_BAD_REQUEST;
		}
		response.setStatus( code );
		
		final PrintWriter out = response.getWriter();
		
		String errorMessage;
		errorMessage = (String) request.getAttribute( ATTR_ERROR_MESSAGE );
		
		// TODO SC_REQUEST_ENTITY_TOO_LARGE, SC_REQUEST_URI_TOO_LONG are not in effect (never called)...
		if ( errorMessage == null )
    		switch ( code ) {
    		case HttpServletResponse.SC_BAD_REQUEST              : errorMessage = "Bad request!"; break;
    		case HttpServletResponse.SC_NOT_FOUND                : errorMessage = "Requested page or resource could not be found!"; break;
    		case HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE : errorMessage = "Too large input! Max allowed input size: 32 MB!"; break;
    		case HttpServletResponse.SC_REQUEST_URI_TOO_LONG     : errorMessage = "Tooooo long URL! Try to specify less parameters in the URL."; break;
    		case HttpServletResponse.SC_INTERNAL_SERVER_ERROR    : errorMessage = "An internal error occured :( We apologize for the inconvenience."; break;
    		default                                              : errorMessage = "Unknown error! (HTTP" + code + ")"; break;
    		}
		
		LOGGER.warning( "Serving error page, code: " + code + ", message: " + errorMessage );
		
		out.println( "<p class=\"error\">" + errorMessage + "</p>" );
		out.println( "<p>Go back to the <a href=\"/\">" + Page.INDEX.name + "</a> page.</p>" );
	}
	
	private static void renderHeader( final HttpServletRequest request, final HttpServletResponse response, final Page page ) throws IOException {
		Utils.setNoCache( response );
		
		response.setContentType( "text/html" );
		response.setCharacterEncoding( "UTF-8" );
		
		final PrintWriter out = response.getWriter();
		out.print( "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\"><link rel=\"stylesheet\" type=\"text/css\" href=\"/pubgoods.css\"/><link href=\"" );
		out.print( page == Page.ERROR ? "/favicon_error.ico" : "/favicon.ico" );
		out.print( "\" rel=\"icon\" type=\"image/x-icon\"/>" );
		out.println( "<title>" + ( page == null ? "Public Services" : page.title + " - Public Services" ) + "</title>" );
		out.println( GA_TRACKER_HTML_SCRIPT );
		out.println( "</head><body>" );
		
		final StringBuffer requestURLBuilder = request.getRequestURL();
		if ( request.isSecure() )
			requestURLBuilder.deleteCharAt( 4 );
		else
			requestURLBuilder.insert( 4, 's' );
		if ( request.getQueryString() != null )
			requestURLBuilder.append( '?' ).append( request.getQueryString() );
		out.println( "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" class=\"header\"><tr><td align=\"left\"><code>&gt; Public Services and Web Tools</code><td align=\"right\"><a style=\"color:#0000ff\" href=\""
			+ requestURLBuilder + "\">Turn SSL encryption " + ( request.isSecure() ? "OFF" : "ON" ) + "</a></table>" );
		
		// Render menu
		out.println( "<p class=\"menuBar\">" );
		boolean first = true;
		for ( final Page page_ : PAGES ) {
			if ( !page_.listable )
				continue;
			if ( first )
				first = false;
			else
				out.print( '|' );
			out.print( "<a href=\"" );
			out.print( page_.path );
			out.print( "\" class=\"" + ( page_ == page ? "activeMenu\">" : "menu\">" ) );
			out.print( page_.name );
			out.println( "</a>" );
		}
		out.println( "</p>" );
		
		out.println( "<h1>" + page.title + "</h1>" );
		
		if ( page != Page.ERROR )
			out.println( HEADER_AD_728_90_HTML_SCRIPT );
		
		if ( page.description != null ) {
			out.print( "<p class=\"description\">" );
			out.print( page.description );
			out.println( "</p>" );
		}
	}
	
	private static void renderFooter( final HttpServletRequest request, final HttpServletResponse response ) throws IOException {
		final PrintWriter out = response.getWriter();
		out.println( "<div class=\"footer\" style=\"text-align:right;font-style:italic\">Contact: <img style=\"vertical-align:text-bottom\" src=\"https://lh6.googleusercontent.com/-E0efyIT_7Dk/TeyYS2puEQI/AAAAAAAAbl4/JN0yyyyzZFY/author_email.png\"> | &copy; Andr&aacute;s Belicza, 2012</div></body></html>" );
	}
	
}
