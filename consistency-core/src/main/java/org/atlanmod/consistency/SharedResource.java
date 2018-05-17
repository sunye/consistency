/*
 *
 *  * Copyright (c) 2013-2017 Atlanmod INRIA LINA Mines Nantes.
 *  * All rights reserved. This program and the accompanying materials
 *  * are made available under the terms of the Eclipse Public License v1.0
 *  * which accompanies this distribution, and is available at
 *  * http://www.eclipse.org/legal/epl-v10.html
 *  *
 *  * Contributors:
 *  *     Atlanmod INRIA LINA Mines Nantes - initial API and implementation
 *
 *
 */

package org.atlanmod.consistency;

import com.google.common.collect.Maps;
import graph.Graph;
import org.atlanmod.consistency.adapter.EObjectAdapter;
import org.atlanmod.consistency.core.*;
import org.atlanmod.consistency.message.UpdateMessage;
import org.atlanmod.consistency.update.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

import java.util.*;

import static org.atlanmod.consistency.util.ConsistencyUtil.adapterFor;

//import org.atlanmod.appa.pubsub.Consumer;
//import org.atlanmod.appa.pubsub.ProducerImpl;

/**
 * Created on 17/02/2017.
 */
public class SharedResource extends ResourceImpl {

    private Map<Id, EObject> contents = Maps.newHashMap();
    //private IdBuilder builder = new IdBuilder();
    private History history = new History(this);
    private ChangeManager manager = new ChangeManager(history);
    private ResourceId rid; //= IdBuilder.generateRID();


    public SharedResource(URI uri) {
        this(uri, IdBuilder.generateRID());
    }

    public SharedResource(URI uri, ResourceId rid) {
        super(uri);
        this.rid = rid;
    }

    public Map<Id, EObject> contents() {
        return contents;
    }

    @Override
    public void attachedHelper(EObject eObject) {
        InstanceId oid;
        EObjectAdapter adapter = adapterFor(eObject);
        if (Objects.isNull(adapter)) {
            oid = rid.nextId();
            eObject.eAdapters().add(new EObjectAdapter(manager,oid));
            contents.put(oid, eObject);
            history.add(new Attach(oid, eObject.eClass()));
        } else {
            oid = adapter.id();
            history.basicAdd(new Attach(oid, eObject.eClass()));
        }

        System.out.println("Adding Id to: " + oid);
    }

    @Override
    public void detachedHelper(EObject eObject) {
        EObjectAdapter adapter = adapterFor(eObject);
        if (Objects.nonNull(adapter)) {
            Id oid = adapter.id();
            System.out.println("--detaching object "+oid+"--");
            eObject.eAdapters().remove(adapter);
            contents.remove(oid);
            history.add(new Detach(oid));
        }
        super.detachedHelper(eObject);
    }

    @Override
    protected boolean isAttachedDetachedHelperRequired() {
        return true;
    }


    /**
     * Called by History.integrate(Operation)
     * Used to recreate an operation locally
     * @param operation the Operation to reproduce
     */
    public void execute(Operation operation) {
        Id oid = operation.instanceId();
        EObject eObject;
        // To recreate the object to be attached
        if (operation instanceof Attach) {
            eObject = new CreateEObject(oid, ((Attach) operation).getEClass()).getObject();
        } else {
            eObject = contents.get(oid);
        }
        eObject.eSetDeliver(false);
        operation.execute(this, eObject);
        eObject.eSetDeliver(true);

        // Because they call add() which automatically calls history.add(). Probably incomplete
        if (!(operation instanceof Attach ||
                operation instanceof SetReference ||
                operation instanceof Detach)) {
            history.add(operation);
        }
    }

    public void cancel(Operation operation) {

    }

    public void broadcast(Operation operation) {
        //producer.send(operation.asMessage());
    }

    /**
     * Receives and deals with and incoming message
     * @param message the message to deal with
     */
    public void receive(UpdateMessage message) {
        Operation operation = null;
        switch (message.type()) {
            case Attach:
                operation = new Attach(message);
                break;
            case Detach:
                operation = new Detach(message);
                break;
            case SetValue:
                operation = new SetValue((FeatureId) message.featureId(), message.value(), message.oldValue());
                break;
            case SetReference:
                operation = new SetReference((FeatureId) message.featureId(), (Id) message.value());
                break;
            case AddReference:
                operation = new AddReference((FeatureId) message.featureId(), (Id) message.value());
                break;
            case Unset:
                operation = new Unset((FeatureId) message.featureId());
                break;
        }
        this.history.integrate(operation);
    }

    /**
     * Checks by semi-recursion whether an object appears in the resource (sub)content
     *
     * @param object the object to search
     * @return true if object is contained by the resource or one of its contents, else false
     */

    public boolean contains(EObject object) {
        boolean containment = contents.containsValue(object);

        if (!containment) {
            for (EObject each : contents.values()) {
                if (each.eContents().contains(object)) {
                    containment = true;
                    break;
                }
            }
        }

        return containment;
    }

    /**
     * A basic output summary of what happened in this resource for the session
     */
    public void summary() {
        int counter;
        boolean plural;

        System.out.println("\n\n------ RESOURCE " + uri + " SUMMARY ------\nRID : " + rid);

        plural = contents.size() > 1;
        System.out.println("\nThere " + (plural ? "are " : "is ") + contents.size() + (plural ? " different EObjects" : " EObject") + " in the resource :\n");

        counter = 1;
        for (EObject each : contents.values()) {
            System.out.println("EObject " + counter++ + " : " + ((each instanceof Graph) ? (each + ((Graph)each).output()) : each));
        }

        plural = history.basicHistory().size() > 1;
        System.out.println("\nThere " + (plural ? "are " : "is ") + history.basicHistory().size() + " registered operation" + (plural ? "s" : "") + " in the resource :\n");

        counter = 1;
        for (Operation each : history.basicHistory()){
            System.out.println("Operation " + counter++ + " : " + each);
        }


        System.out.println("\n---------------------------- END OF RESOURCE ----------------------------");
    }

    public History getHistory() {
        return history;
    }

    public EObject contentAt(int i) {
        EObject[] elts = contents.values().toArray(new EObject[0]);
        List<EObject> objects = new ArrayList<>(Arrays.asList(elts));

        return objects.get(i);
    }

    public class ConsumerThread implements Runnable {
        @Override
        public void run() {
            /*while (true) {
                UpdateMessage message = (UpdateMessage) consumer.receive(3000);
            }*/

        }
    }

}
