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

package fr.inria.atlanmod.consistency.update;

import fr.inria.atlanmod.consistency.core.FeatureId;
import fr.inria.atlanmod.consistency.core.Id;
import fr.inria.atlanmod.consistency.core.InstanceId;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static fr.inria.atlanmod.consistency.util.ConsistencyUtil.identifierFor;
import static fr.inria.atlanmod.consistency.util.ConsistencyUtil.isEAttribute;
import static fr.inria.atlanmod.consistency.util.ConsistencyUtil.isEReference;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Created by sunye on 17/02/2017.
 */
public class ChangeManager {

    private BlockingQueue<Operation> incoming = new LinkedBlockingQueue<Operation>();

    public void notifyChanged(InstanceId oid, Notification notification) {
        assert nonNull(notification);
        assert nonNull(notification.getNotifier());
        assert EObject.class.isAssignableFrom(notification.getNotifier().getClass());

        if(isNull(notification.getFeature())) {return;}
        int type = notification.getEventType();
        Operation op;

        switch (type) {

            case Notification.SET :
                op = set(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.UNSET:
                op = unset(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.ADD:
                op = add(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.REMOVE:
                op = remove(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.MOVE:
                op = move(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.ADD_MANY:
                op = addMany(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.REMOVE_MANY:
                op = removeMany(oid, notification);
                newLocalOperation(op);
                break;
            case Notification.REMOVING_ADAPTER:
                System.out.println("--removing adapter--");
                break;
            case Notification.NO_FEATURE_ID: break;
            case Notification.RESOLVE: break;

            default: break;
        }

    }

    private Operation set(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "Set of a null feature";
        assert nonNull(notification.getNewValue()) : "Set with a null value";

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);

        if (isEAttribute(feature)) {
            return new SetValue(fid, notification.getNewValue());
        } else if (isEReference(feature)) {
            return new SetReference(fid, identifierFor((EObject) notification.getNewValue()));
        } else {
            return new Invalid();
        }
    }

    private Operation unset(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "Unset of a null feature";

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);
        return new Unset(fid);
    }

    private Operation add(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "Add of a null feature";
        assert nonNull(notification.getNewValue()) : "Add with a null value";

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);

        if (isEAttribute(feature)) {
            return new AddValue(fid, notification.getNewValue());
        } else if (isEReference(feature)) {
            return new AddReference(fid, identifierFor((EObject) notification.getNewValue()));
        } else {
            return new Invalid();
        }
    }

    private Operation remove(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "Remove of a null feature";
        assert nonNull(notification.getOldValue()) : "Remove with a null old value";

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);

        if (isEAttribute(feature)) {
            return new RemoveValue(fid, notification.getOldValue());
        } else if (isEReference(feature)) {
            return new RemoveReference(fid, identifierFor((EObject) notification.getOldValue()));
        } else {
            return new Invalid();
        }
    }

    private Operation move(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "Move of a null feature";
        System.out.println(notification);

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);
        return new MoveValue(fid, notification.getOldValue(), notification.getPosition());
    }

    private Operation addMany(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "AddMany of a null feature";
        assert nonNull(notification.getNewValue()) : "AddMany with a null value";

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);

        if (isEAttribute(feature)) {
            List<Object> values = (List<Object>) notification.getNewValue();
             return new AddManyValues(fid, values);
        } else if (isEReference(feature)) {
            List<EObject> values = (List<EObject>) notification.getNewValue();
            List<Id> ids = values.stream()
                    .map(each -> identifierFor(each))
                    .collect(Collectors.toList());

            return new AddManyReferences(fid, ids);
        } else {
            return new Invalid();
        }
    }

    private Operation removeMany(InstanceId oid, Notification notification) {
        assert nonNull(notification.getFeature()) : "RemoveMany of a null feature";
        assert nonNull(notification.getNewValue()) : "RemoveMany with a null value";

        EStructuralFeature feature = (EStructuralFeature) notification.getFeature();
        FeatureId fid = oid.withFeature(feature);

        if (isEAttribute(feature)) {
            List<Object> values = (List<Object>) notification.getNewValue();
            return new RemoveManyValues(fid, values);
        } else if (isEReference(feature)) {
            List<EObject> values = (List<EObject>) notification.getNewValue();
            List<Id> ids = values.stream()
                    .map(each -> identifierFor(each))
                    .collect(Collectors.toList());
            return new RemoveManyReferences(fid, ids);
        } else {
            return new Invalid();
        }
    }

    private void newLocalOperation(Operation operation) {
        incoming.offer(operation);
        System.out.println("New operation: " + operation);
    }
}
