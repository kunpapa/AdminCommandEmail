
/*
 * Copyright 2016 kunpapa. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice, this list of
 *        conditions and the following disclaimer.
 *
 *     2. Redistributions in binary form must reproduce the above copyright notice, this list
 *        of conditions and the following disclaimer in the documentation and/or other materials
 *        provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  The views and conclusions contained in the software and documentation are those of the
 *  authors and contributors and should not be interpreted as representing official policies,
 *  either expressed or implied, of anybody else.
 */

package net.deadplayers.admincommandemail;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

import net.deadplayers.maillibrary.MailLibrary;


/**
 * Main class of AdminCommandEmail
 * @author kunpapa
 *
 */
public class AdminCommandEmail extends JavaPlugin implements Listener{
	
	private HashMap<String, List<String>> playerCommands = new HashMap<>();
	private String serverName;
	private String subjectMsg;
	private String bodyMsg;
	private String mailTo;
	private boolean logOp;
	private int timeInMinutes;
	private List<String> whitelistCommands;
	private List<String> blacklistCommands;

	private MailLibrary mailLibrary;
	
	private Metrics metrics;

	private boolean hookMailLibrary() {
		final Plugin plugin = this.getServer().getPluginManager().getPlugin("MailLibrary");
		mailLibrary = MailLibrary.class.cast(plugin);
		return mailLibrary != null; 
	}

	@Override
	public void onEnable(){
		Bukkit.getLogger().info("Enabling plugin...");
		this.saveDefaultConfig();
		loadConfiguration();
		this.handleMetrics();
		if(!hookMailLibrary()){
			Bukkit.getLogger().info("Disabling plugin - MailLibrary is not installed");
			Bukkit.getLogger().info("Download needed library from this url: https://www.spigotmc.org/resources/maillibrary.28753/");
			this.getServer().getPluginManager().disablePlugin(this);
			return;
		}
		this.getServer().getPluginManager().registerEvents(this, this);
		Bukkit.getLogger().info("Plugin enabled!");
	}
	
	private void loadConfiguration(){
		serverName = this.getConfig().getString("serverName");
		subjectMsg = this.getConfig().getString("emailSubject").replaceAll("%serverName%", serverName);
		bodyMsg = this.getConfig().getString("emailBody").replaceAll("%serverName%", serverName);
		mailTo = this.getConfig().getString("emailsToSend");
		logOp = this.getConfig().getBoolean("logOpUsers");
		timeInMinutes = this.getConfig().getInt("timeInMinutes");
		whitelistCommands = this.getConfig().getStringList("whitelistCommands");
		blacklistCommands = this.getConfig().getStringList("blacklistCommands");
		//set all list to lowercase
		for(int i = 0; i<whitelistCommands.size(); i++){
			whitelistCommands.set(i, "/"+whitelistCommands.get(i).toLowerCase());
		}
		for(int i = 0; i<blacklistCommands.size(); i++){
			blacklistCommands.set(i, "/"+blacklistCommands.get(i).toLowerCase());
		}
	}
	
	private void handleMetrics(){
		if(this.getConfig().contains("metrics") && this.getConfig().getBoolean("metrics")){
			try {
				metrics = new Metrics(this);
				metrics.start();
			} catch (IOException e) {
				// Failed to submit the stats :-(
			}
		} else if (metrics != null){
			try {
				metrics.disable();
			} catch (IOException e) {
				// Failed to disable
			}
		}
	}
	
	private void reloadConfiguration(){
		this.reloadConfig();
		this.loadConfiguration();
		this.handleMetrics();
	}
	
	@Override
	public void onDisable(){
		this.sendMailsNow(true);
		Bukkit.getLogger().info("Plugin disabled!");
	}
	
	private void sendMailsNow(boolean sync){
	    Iterator<Entry<String, List<String>>> it = playerCommands.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, List<String>> pair = it.next();
	        sendCommandsToEmail((String) pair.getKey(), sync);
	        it.remove();
	    }
	    Bukkit.getScheduler().cancelTasks(this);
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerExecuteCommand(PlayerCommandPreprocessEvent e){
		if(e.getPlayer().isOp() && !logOp) //avoid op to log
			return;
		if(e.getPlayer().hasPermission("admincommandemail.logcommands")){
			String lowerMsg = e.getMessage().toLowerCase();
			if(!blacklistCommands.isEmpty()){
				for(String blacklist : blacklistCommands){
					if(lowerMsg.startsWith(blacklist)){
						addCommandToPlayer(e.getPlayer().getName(), e.getMessage());
						return;
					}
				}
			} else {
				for(String whitelist : whitelistCommands){
					if(lowerMsg.startsWith(whitelist)){
						//no need to log this command
						return;
					}
				}
				addCommandToPlayer(e.getPlayer().getName(), e.getMessage());
			}
		}
	}
	
	private void addCommandToPlayer(String playerName, String command){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		if(!playerCommands.containsKey(playerName)){
			playerCommands.put(playerName, new ArrayList<String>());
			initCountdown(playerName);
		}
		playerCommands.get(playerName).add(dateFormat.format(date)+" -> "+command);
	}
	
	private void initCountdown(final String playername){
		Bukkit.getScheduler().runTaskLater(this, new Runnable(){

			@Override
			public void run() {
				sendCommandsToEmail(playername);
				playerCommands.remove(playername);
			}
			
		}, 20L * 60 * timeInMinutes);
	}
	
	private void sendCommandsToEmail(final String playername){
		sendCommandsToEmail(playername, false);
	}
	
	private void sendCommandsToEmail(final String playername, boolean sync){
		List<String> commands = playerCommands.get(playername);
		if(commands == null)
			return;
		StringBuilder body = new StringBuilder();
		body.append(bodyMsg.replaceAll("%playerName%", playername));
		body.append("<ul>");
		for(String s : commands){
			body.append("<li>"+s+"</li>");
		}
		body.append("</ul>");
		
		if(!sync)
			mailLibrary.sendMail(subjectMsg.replaceAll("%playerName%", playername),body.toString(), mailTo);
		else
			mailLibrary.sendMailSync(subjectMsg.replaceAll("%playerName%", playername),body.toString(), mailTo);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("admincommandemail") && (sender.isOp() || sender.hasPermission("admincommandemail.admin"))) {
			if(args.length>0){
				if(args[0].equalsIgnoreCase("sendnow")){
					sendMailsNow(false);
					sender.sendMessage(ChatColor.GOLD+"Sending all pendings emails now!");
				}
				else if (args[0].equalsIgnoreCase("reload")){
					this.reloadConfiguration();
					sender.sendMessage(ChatColor.GOLD+"Plugin AdminCommandEmail reloaded!");
				}
				else {
					sendHelp(sender);	
				}
			} else {
				sendHelp(sender);
			}
		}
		return true;
	}
	
	private void sendHelp(CommandSender sender){
		sender.sendMessage(ChatColor.RED+"AdminCommandEmail Help:");
		sender.sendMessage(ChatColor.GOLD+"/ace sendnow -> Send all pending emails now");
		sender.sendMessage(ChatColor.GOLD+"/ace reload -> reload configuration");
	}

}
