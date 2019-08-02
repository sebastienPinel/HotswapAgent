/*
 * Copyright 2013-2019 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.weld.beans;

import java.util.Iterator;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.inject.Singleton;

import org.hotswap.agent.logging.AgentLogger;
import org.jboss.weld.bean.ManagedBean;

/**
 * The Class ContextualReloadHelper.
 *
 * @author alpapad@gmail.com
 */
public class ContextualReloadHelper {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ContextualReloadHelper.class);

    public static <T> void reload(WeldHotswapContext ctx) {
        Set<Contextual<T>> beans = ctx.$$ha$getBeansToReloadWeld();

        if (beans != null && !beans.isEmpty()) {
            LOGGER.debug("Starting re-loading Contextuals in {}, {}", ctx, beans.size());

            Iterator<Contextual<T>> it = beans.iterator();
            while (it.hasNext()) {
                final Contextual<T> managedBean = it.next();
                final Context cdiCtx = (Context) ctx;
                if (cdiCtx.getScope().equals(ApplicationScoped.class) || cdiCtx.getScope().equals(Singleton.class)){
                    reinitialize(cdiCtx, managedBean);
                } else {
                    destroy(ctx, managedBean);
                }
            }
            beans.clear();
            LOGGER.debug("Finished re-loading Contextuals in {}", ctx);
        }
    }

    /**
     * Tries to add the bean in the context so it is reloaded in the next activation of the context.
     *
     * @param ctx
     * @param managedBean
     * @return
     */
    public static <T> boolean addToReloadSet(final Context ctx,  final Contextual<T> managedBean)  {
        if (!WeldHotswapContext.class.isAssignableFrom(ctx.getClass())) {
            LOGGER.warning("Context {} is not patched. Can not add {} to reload set", ctx, managedBean);
            return false;
        }

        LOGGER.debug("Adding bean in '{}' : {}", ctx.getClass(), managedBean);
        final WeldHotswapContext context = (WeldHotswapContext) ctx;
        context.$$ha$addBeanToReloadWeld(managedBean);
        // Eagerly reloading weld
        context.isActive();
        return true;
    }

    /**
     * Will remove bean from context forcing a clean new instance to be created (eg calling post-construct)
     *
     * @param ctx
     * @param managedBean
     */
    public static <T> void destroy(final WeldHotswapContext ctx, Contextual<T> managedBean ) {
        try {
            LOGGER.debug("Removing Contextual from Context........ {},: {}", managedBean, ctx);
            T get = ctx.get(managedBean);
            if (get != null) {
                ctx.destroy(managedBean);
            }
            get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.error("Error removing ManagedBean {}, it still exists as instance {} ", managedBean, get);
                ctx.destroy(managedBean);
            }
        } catch (Exception e) {
            LOGGER.error("Error destroying bean {},: {}", e, managedBean, ctx);
        }
    }

    /**
     * Will re-inject any managed beans in the target. Will not call any other life-cycle methods
     *
     * @param ctx
     * @param managedBean
     */
    public static <T> void reinitialize(Context ctx, Contextual<T> contextual) {
        try {
            ManagedBean<T> managedBean = ManagedBean.class.cast(contextual);
            LOGGER.debug("Re-Initializing........ {},: {}", managedBean, ctx);
            T get = ctx.get(managedBean);
            if (get != null) {
                LOGGER.debug("Bean injection points are reinitialized '{}'", managedBean);
                managedBean.getProducer().inject(get, managedBean.getBeanManager().createCreationalContext(managedBean));
            }
        } catch (Exception e) {
            LOGGER.error("Error reinitializing bean {},: {}", e, contextual, ctx);
        }
    }
}
