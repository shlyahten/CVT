package ru.shlyahten.cvt.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ExpressionEvaluator.
 */
class ExpressionEvaluatorTest {
    
    @Test
    fun `evaluator calculates simple addition correctly`() {
        val vars = mapOf("A" to 10.0, "B" to 20.0)
        val result = ExpressionEvaluator.eval("A+B", vars)
        assertEquals(30.0, result, 0.01)
    }
    
    @Test
    fun `evaluator calculates multiplication correctly`() {
        val vars = mapOf("A" to 5.0, "B" to 4.0)
        val result = ExpressionEvaluator.eval("A*B", vars)
        assertEquals(20.0, result, 0.01)
    }
    
    @Test
    fun `evaluator calculates power correctly`() {
        val vars = mapOf("N" to 3.0)
        val result = ExpressionEvaluator.eval("N^2", vars)
        assertEquals(9.0, result, 0.01)
    }
    
    @Test
    fun `evaluator calculates temp formula 1 correctly`() {
        // Test with N=100 (typical value)
        val n = 100.0
        val vars = mapOf("N" to n)
        val formula = "(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)"
        val result = ExpressionEvaluator.eval(formula, vars)
        
        // Expected: calculate manually or from reference
        // N=100: 2.344 + -138.7 + 319.3 + -350.1 + 230.2 + -36.6 = 26.444
        assertEquals(26.44, result, 0.1)
    }
    
    @Test
    fun `evaluator calculates temp formula 2 correctly`() {
        val n = 100.0
        val vars = mapOf("N" to n)
        val formula = "(0.0000286*N*N*N)+(-0.00951*N*N)+(1.46*N)+(-30.1)"
        val result = ExpressionEvaluator.eval(formula, vars)
        
        // N=100: 28.6 + -95.1 + 146 + -30.1 = 49.4
        assertEquals(49.4, result, 0.1)
    }
    
    @Test
    fun `evaluator handles parentheses correctly`() {
        val vars = mapOf("A" to 2.0, "B" to 3.0, "C" to 4.0)
        val result = ExpressionEvaluator.eval("(A+B)*C", vars)
        assertEquals(20.0, result, 0.01)
    }
    
    @Test
    fun `evaluator handles unary minus correctly`() {
        val vars = emptyMap<String, Double>()
        val result = ExpressionEvaluator.eval("-5+3", vars)
        assertEquals(-2.0, result, 0.01)
    }
    
    @Test(expected = Exception::class)
    fun `evaluator throws on unknown variable`() {
        val vars = mapOf("A" to 10.0)
        ExpressionEvaluator.eval("A+B", vars)
    }
}
