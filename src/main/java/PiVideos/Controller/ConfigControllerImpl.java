package PiVideos.Controller;

import PiVideos.Model.ClientPi;
import PiVideos.Model.Network;
import PiVideos.Service.FeatureService;
import PiVideos.Service.SocketService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*******************************************************************************************************
 * Autor: Julian Hecht
 * Datum letzte Änderung: 12.05.2025
 * Änderung: Refactoring für Redirects
 *******************************************************************************************************/

@Controller
@RequestMapping("features/config")
public class ConfigControllerImpl implements ConfigController {

    @Autowired
    SocketService socketSerivce;

    @Autowired
    FeatureService featureService;

    @Autowired
    private RestTemplate restTemplate;

    // -------> CLient Methoden <-------
    // Übersicht über Clients und Mögichkeit zum Anlegen neuer Clients
    @Override
    @GetMapping("/client")
    public String getConfigClient(HttpSession session, Model model) {
        Network network = (Network) session.getAttribute("network");

        List<ClientPi> clients = featureService.getAllClientPis(network);
        Map<Integer, Integer> videoCounts = new HashMap<>();
        Map<Integer, LocalDateTime> latestVids = new HashMap<>();

        for (ClientPi client : clients) {
            int count = featureService.countVidsForClient(network, client);
            LocalDateTime latestVideo = featureService.getLatestVideo(network, client);
            videoCounts.put(client.get_id(), count);
            latestVids.put(client.get_id(), latestVideo);
        }

        model.addAttribute("clients", clients);
        model.addAttribute("videoCounts", videoCounts);
        model.addAttribute("latestVids", latestVids);
        model.addAttribute("clientPi", new ClientPi());

        return "features/configuration/clientPi.html";
    }

    // Speichert einen neuen ClientPi
    @Override
    @PostMapping("/client/save")
    public String saveConfigClient(@ModelAttribute ClientPi clientPi, HttpSession session) {
        Network network = (Network) session.getAttribute("network");
        clientPi.setNetwork(network);
        featureService.saveClient(clientPi);
        return "redirect:/features/config/client";
    }

    // Löscht einen bestimmten ClientPi durch die ID
    @PostMapping("/client/delete/{_id}")
    public String deleteConfigClient(@PathVariable("_id") Integer _id, RedirectAttributes redirectAttributes) {

        if(featureService.deleteClientPiById(_id)){
            redirectAttributes.addFlashAttribute("message","ClientPi mit ID: " + _id + "  und alle zugehörigen Videos gelöscht");
        } else {
            redirectAttributes.addFlashAttribute("error", "Fehler beim Löschen des ClientPi. Bitte versuchen Sie es erneut.");
        }
        return "redirect:/features/config/client";
    }

    // Updatet einen bestimmten ClientPi durch die ID
    @PostMapping("/client/update/{_id}")
    public String updateConfigClient(@PathVariable("_id") Integer _id, RedirectAttributes redirectAttributes,
            @RequestParam String location, @RequestParam String comment) {
        Optional<ClientPi> optClient = featureService.getClientBy_id(_id);
        if (optClient.isPresent()) {
            ClientPi clientPi = optClient.get();
            clientPi.setLocation(location);
            clientPi.setComment(comment);

            if (featureService.updateClient(clientPi)) {
                redirectAttributes.addAttribute("message", "Client mit der ID " + _id + " wurde geändert.");
            } else {
                redirectAttributes.addAttribute("error",
                        "Client mit der ID " + _id + " konnte nicht geändert werden (nicht gefunden?).");
            }
        }
        return "redirect:/features/config/client";
    }

    // Startet einen bestimmten ClientPi durch die ID
    // Hier wird ein POST Request an den ClientPi Flaskagent gesendet, der den
    // ClientPi startet
    @PostMapping("/client/start/{id}")
    public String startClient(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            ClientPi client = featureService.getClientBy_id(Integer.parseInt(id)).orElseThrow();
            String url = "http://" + client.getName() + ".local:5000/start";
            restTemplate.postForObject(url, null, String.class);
            redirectAttributes.addFlashAttribute("success", "Client erfolgreich gestartet.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Fehler beim Starten des Client: Client Agent starten!");
        }
        redirectAttributes.addFlashAttribute("stoppedClientId", id);
        return "redirect:/?startedClientId=" + id;
    }

    // Stoppt einen bestimmten ClientPi durch die ID
    // Hier wird ein POST Request an den ClientPi Flaskagent gesendet, der den
    // ClientPi stoppt
    @PostMapping("/client/stop/{id}")
    public String stopClient(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            ClientPi client = featureService.getClientBy_id(Integer.parseInt(id)).orElseThrow();
            String url = "http://" + client.getName() + ".local:5000/stop";
            restTemplate.postForObject(url, null, String.class);
            redirectAttributes.addFlashAttribute("success", "Client erfolgreich gestoppt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Fehler beim Stoppen des Clients: Client Agent starten!");
        }
        redirectAttributes.addFlashAttribute("stoppedClientId", id);
        return "redirect:/?stoppedClientId=" + id;
    }

    // Restartet einen bestimmten ClientPi durch die ID
    // Hier wird ein POST Request an den ClientPi Flaskagent gesendet, der den
    // ClientPi Restartet
    @PostMapping("/client/restart/{id}")
    public String restartClient(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            ClientPi client = featureService.getClientBy_id(Integer.parseInt(id)).orElseThrow();
            String url = "http://" + client.getName() + ".local:5000/restart";
            restTemplate.postForObject(url, null, String.class);
            redirectAttributes.addFlashAttribute("message",
                    "Client " + client.getName() + " erfolgreich neu gestartet.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Fehler beim Neustarten des Clients: Client Agent starten!");
        }
        redirectAttributes.addFlashAttribute("stoppedClientId", id);
        return "redirect:/features/config/client";
    }

    // -------> Netzwerk Methoden <-------
    // Öffnet die Netzwerkkonfiguration des aktuellen Netzwerks
    @Override
    @GetMapping("/network")
    public String getNetworkConifg(Model model) {
        return "features/configuration/network.html";
    }

    // Bearbeitet das Aktuelle Netzwerk der Session
    @PostMapping("/network/update")
    public String updateNetwork(@RequestParam Integer id,
            @RequestParam String name,
            @RequestParam String rootPath,
            @RequestParam int port,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Network net = featureService.getNetworkById(id);
        net.setName(name);
        net.setRootPath(rootPath);
        net.setPort(port);

        if(featureService.saveNetwork(net)) {
            session.setAttribute("network", net);
            redirectAttributes.addFlashAttribute("message", "Netzwerk mit ID: " + id + " geändert");
        } else {
            redirectAttributes.addFlashAttribute("error", "Fehler beim Aktualisieren des Netzwerks.");
        }
  
        return "redirect:/features/config/network";
    }

    // Löscht das Aktuelle Netzwerk der Session
    @PostMapping("/network/delete")
    public String deleteNetwork(@RequestParam Integer id, HttpSession session, RedirectAttributes redirectAttributes) {
        
        if(featureService.deleteNetworkByID(id)) {
            session.setAttribute("network", null);
            redirectAttributes.addFlashAttribute("message", "Netzwerk mit ID: " + id + " gelöscht");
        } else {
            redirectAttributes.addFlashAttribute("error", "Fehler beim Löschen des Netzwerks. Bitte versuchen Sie es erneut.");
        }
        return "redirect:/login";
    }

    // -------> Socket Methoden <-------
    // Socket für das Netzwerk Starten und Stoppen für das aktuelle Netzwerk der
    // Session
    @PostMapping("/startServer")
    public String startServer(HttpSession session, RedirectAttributes model) {
        Network network = (Network) session.getAttribute("network");
        socketSerivce.startServer(network);
        session.setAttribute("socket", true);
        return "redirect:/";
    }

    @Override
    @PostMapping("/stopServer")
    public String stopServer(HttpSession session, RedirectAttributes model) {
        Network network = (Network) session.getAttribute("network");
        socketSerivce.stopServerForNetwork(network);
        session.setAttribute("socket", false);
        return "redirect:/";
    }

}
