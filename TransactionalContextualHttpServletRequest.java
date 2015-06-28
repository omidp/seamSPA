import javax.servlet.http.HttpServletRequest;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.jboss.seam.servlet.ContextualHttpServletRequest;
import org.jboss.seam.transaction.Transaction;
import org.jboss.seam.transaction.UserTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Omid Pourhadi
 *
 */
public abstract class TransactionalContextualHttpServletRequest extends ContextualHttpServletRequest
{

    protected final static Logger logger = LoggerFactory.getLogger(TransactionalContextualHttpServletRequest.class);
    
    public TransactionalContextualHttpServletRequest(HttpServletRequest request)
    {
        super(request);
    }

    @Override
    public void process() throws Exception
    {
        work();
    }

    private void work()
    {
        UserTransaction userTransaction = null;
        try
        {
            userTransaction = Transaction.instance();
            userTransaction.begin();
            joinTransaction();
            workInTransaction();
            userTransaction.commit();
        }
        catch (SecurityException e)
        {
            logger.info(e.getMessage());
        }
        catch (IllegalStateException e)
        {
            logger.info(e.getMessage());
        }
        catch (RollbackException e)
        {
            logger.info(e.getMessage());
        }
        catch (HeuristicMixedException e)
        {
            logger.info(e.getMessage());
        }
        catch (HeuristicRollbackException e)
        {
            logger.info(e.getMessage());
        }
        catch (SystemException e)
        {
            logger.info(e.getMessage());
        }
        catch (NotSupportedException e)
        {
            logger.info(e.getMessage());
        }
    }

    protected abstract void joinTransaction();
    protected abstract void workInTransaction();

}
