package com.omidbiz.action;

import java.io.IOException;

import javax.persistence.EntityManager;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.seam.Component;
import org.jboss.seam.servlet.ContextualHttpServletRequest;
import org.jboss.seam.transaction.Transaction;
import org.jboss.seam.transaction.UserTransaction;

import com.omidbiz.model.User;

public class ServletSPA extends HttpServlet
{

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
    }

    @Override
    protected void doPost(final HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        new RequestProcessor<User>(req)
        {

            @Override
            public void process() throws Exception
            {
                User u = getInstance();
                UserTransaction userTransaction = Transaction.instance();
                userTransaction.begin();
                EntityManager entityManager = (EntityManager) Component.getInstance("entityManager");
                entityManager.joinTransaction();
                entityManager.persist(u);
                userTransaction.commit();
            }
            
        }.run();
    }

}
