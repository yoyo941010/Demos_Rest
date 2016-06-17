package es.usal.tfg;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import es.usal.tfg.security.PasswordStorage;
import es.usal.tfg.security.PasswordStorage.CannotPerformOperationException;

@Singleton
@Path("/campaign")
public class CampaignManagement {

	public static final String WEBSERVICE_ABSOLUTE_ROUTE = "/demos";
	
	@POST
	@Path("/register")
	@Consumes("application/x-www-form-urlencoded")
	public Response register (@FormParam("campaign") String campaign, @FormParam("password") String password) {
		
		String hashPass=null;
		try {
			hashPass = PasswordStorage.createHash(password);
		} catch (CannotPerformOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return Response.status(500).entity("Error creando hash de la contraseña").build();
		}
		
		CampaignCredentials campaignCred = new CampaignCredentials(campaign, hashPass);
		
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		System.out.println(gson.toJson(campaignCred));
		try {
			Writer wr = new OutputStreamWriter( new FileOutputStream( WEBSERVICE_ABSOLUTE_ROUTE+ "/campaigns.json", true));
			gson.toJson(campaignCred, wr);
			
			wr.flush();
			wr.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return Response.status(500).entity("Error creando hash de la contraseña").build();
		}
		
		return Response.status(200).entity(gson.toJson(campaignCred)).build();
	}
}
