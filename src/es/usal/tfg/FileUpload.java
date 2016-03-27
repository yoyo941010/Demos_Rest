package es.usal.tfg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Path("/files")
public class FileUpload {

	/**Update the following constant to the desired location for the uploads*/
	private static final String SERVER_UPLOAD_LOCATION_FOLDER = "/home/aythae/Escritorio/files/";

	/**
	 * Upload a File
	 * Reference : https://examples.javacodegeeks.com/enterprise-java/rest/jersey/jersey-file-upload-example/
	 */

	@POST
	@Path("/upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(FormDataMultiPart form) {

		 FormDataBodyPart filePart = form.getField("file");

		 ContentDisposition headerOfFilePart =  filePart.getContentDisposition();

		 InputStream fileInputStream = filePart.getValueAs(InputStream.class);

		 String filePath = SERVER_UPLOAD_LOCATION_FOLDER + headerOfFilePart.getFileName();

		// save the file to the server
		saveFile(fileInputStream, filePath);

		String output = "File saved to server location: " + filePath;

		return Response.status(200).entity(output).build();

	}

	// save uploaded file to a defined location on the server
	private void saveFile(InputStream uploadedInputStream, String serverLocation) {

		try {
			OutputStream outpuStream ;
			int read = 0;
			byte[] bytes = new byte[1024];

			outpuStream = new FileOutputStream(new File(serverLocation));
			while ((read = uploadedInputStream.read(bytes)) != -1) {
				outpuStream.write(bytes, 0, read);
			}

			outpuStream.flush();
			outpuStream.close();

			uploadedInputStream.close();
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

}

