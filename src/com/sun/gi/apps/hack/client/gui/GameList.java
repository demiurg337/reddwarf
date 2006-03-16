/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/


/*
 * GameList.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 9:20:13 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client.gui;

import com.sun.gi.apps.hack.client.LobbyListener;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import javax.swing.ListModel;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
class GameList implements ListModel, LobbyListener
{

    //
    private ArrayList<GameDetail> data;

    //
    private HashSet<ListDataListener> listeners;

    //
    private LobbyPanel lobbyPanel;

    /**
     *
     */
    public GameList(LobbyPanel lobbyPanel) {
        data = new ArrayList<GameDetail>();
        listeners = new HashSet<ListDataListener>();

        this.lobbyPanel = lobbyPanel;
    }

    /**
     *
     */
    public void clearList() {
        data.clear();
        notifyChange();
    }

    /**
     *
     */
    public void gameAdded(String game) {
        data.add(new GameDetail(game, 0));
        notifyChange();
    }

    /**
     *
     */
    public void gameRemoved(String game) {
        for (GameDetail detail : data) {
            if (detail.name.equals(game)) {
                data.remove(detail);
                break;
            }
        }

        notifyChange();
    }

    /**
     *
     */
    public void playerCountUpdated(int count) {
        lobbyPanel.updateLobbyCount(count);
    }

    /**
     *
     */
    public void playerCountUpdated(String game, int count) {
        for (GameDetail detail : data) {
            if (detail.name.equals(game)) {
                detail.count = count;
                break;
            }
        }

        notifyChange();
    }

    /**
     *
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        lobbyPanel.setCharacters(characters);
    }

    private void notifyChange() {
        ListDataEvent event =
            new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED,
                              0, data.size() - 1);
        for (ListDataListener listener : listeners)
            listener.contentsChanged(event);
    }

    public void addListDataListener(ListDataListener l) {
        listeners.add(l);
    }

    public Object getElementAt(int index) {
        return data.get(index);
    }

    public String getNameAt(int index) {
        return data.get(index).name;
    }

    public int getSize() {
        return data.size();
    }

    public void removeListDataListener(ListDataListener l) {
        listeners.remove(l);
    }

    /**
     *
     */
    class GameDetail {
        public String name;
        public int count;
        public GameDetail(String name, int count) {
            this.name = name;
            this.count = count;
        }
        public String toString() {
            return name + " (" + count + ")";
        }
    }

}
