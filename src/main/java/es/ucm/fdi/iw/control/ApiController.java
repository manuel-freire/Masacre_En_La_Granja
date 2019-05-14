package es.ucm.fdi.iw.control;

import es.ucm.fdi.iw.model.Game;
import es.ucm.fdi.iw.model.Status;
import es.ucm.fdi.iw.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.persistence.EntityManager;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpSession;
import javax.transaction.Transactional;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("api")
public class ApiController {

	private static final Logger log = LogManager.getLogger(ApiController.class);

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private IwSocketHandler iwSocketHandler;

	@PostMapping("chat/enviar")
	public ResponseEntity<?> enviar(HttpSession session, 
			@RequestBody String mensaje) {
		User user = (User) session.getAttribute("user"); // <-- este usuario no está conectado a la bd
		user = entityManager.find(User.class, user.getId()); // <-- obtengo usuario de la BD
		
		for(Game game : user.getGames())
		{
			log.info(game.toString());
		}
		
		Game g = user.getActiveGame();

		List<User> users = new ArrayList<>(g.getUsers());
		String message = "{"
				+ "\"chatMessage\": {"
					+ "\"propietario\":\"" 
					+ user.getName() + "\","
					+ "\"mensaje\":\"" 
					+ mensaje + "\"}}";
		Status s = g.getStatusObj();
		String rolPropietario = s.players.get(user.getName());
		for (User u : users) {
			if (rolPropietario.equals("MUERTO") && s.players.get(u.getName()).equals("MUERTO")) {
				iwSocketHandler.sendText(u.getName(), message);
			} else if (s.dia == 0) {
				if (!rolPropietario.equals("MUERTO") && !s.players.get(u.getName()).equals("MUERTO")) {
					iwSocketHandler.sendText(u.getName(), message);
				}
			} else if (s.dia == 1) {
				if (rolPropietario.equals("VAMPIRE") && s.players.get(u.getName()).equals("VAMPIRE")) {
					iwSocketHandler.sendText(u.getName(), message);
				}
			}
		}
		log.debug("Mensaje enviado [{}]", mensaje);
		return ResponseEntity.status(HttpStatus.OK).build();
	}


	// Función para comprobar que el nombre del user que se va a registrar no existe
	@PostMapping("user/loginOk/{name}")
	public ResponseEntity<?> existingName(@PathVariable String name) {
		// Mirar en la base de datos mágicamente para ver si está creado
		Long usersWithLogin = entityManager.createNamedQuery("User.HasName", Long.class).setParameter("userName", name)
				.getSingleResult();
		// si creado
		if (usersWithLogin == 0)
			return ResponseEntity.status(HttpStatus.OK).build();
		return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
	}


	@PostMapping("/game/recivePlay")
	@Transactional
	public ResponseEntity<?> recivePlay(HttpSession session,
			@RequestBody String jugada) {

		User user = (User) session.getAttribute("user"); // <-- este usuario no está conectado a la bd
		user = entityManager.find(User.class, user.getId()); // <-- obtengo usuario de la BD

		for(Game game : user.getGames())
		{
			log.info(game.toString());
		}
		
		Game g = user.getActiveGame();

		List<User> users = new ArrayList<>(g.getUsers());

		List<String> result = procesarJugada(jugada, g.getStatus());
		String nuevoEstado = result.get(1);
		g.setStatus(nuevoEstado);
		entityManager.persist(g);
		entityManager.flush();

		String object = result.get(0);

		String message = "{"
				+ "\"nuevoEstado\":\"" 
					+ object + "\"}";

		for (User u : users) {
			iwSocketHandler.sendText(u.getName(), message);
		}
		log.debug("Jugada enviada: [{}]", jugada);
		return ResponseEntity.status(HttpStatus.OK).build();
	}

	private List<String> procesarJugada(String jugada, String state){

		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("JavaScript");
		// read script file
		try {
			engine.eval(new InputStreamReader(getClass().getResourceAsStream(
					"/static/js/modelo.js"))); // relativo a src/main/resources
		} catch (ScriptException e) {
			log.warn("Error loading script",  e);
		}

		Invocable inv = (Invocable) engine;
		String result = null;
		// call function from script file
		try {
			result = (String) inv.invokeFunction("recivePlay", state, jugada);
		} catch (NoSuchMethodException | ScriptException e) {
			log.warn("Error running script",  e);
		}
		log.warn(result);
		return null;
	}
}