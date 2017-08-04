/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.typeresolution.typeinference;

import net.sourceforge.pmd.lang.java.typeresolution.typedefinition.JavaTypeDefinition;

import static net.sourceforge.pmd.lang.java.typeresolution.typeinference.InferenceRuleType.EQUALITY;
import static net.sourceforge.pmd.lang.java.typeresolution.typeinference.InferenceRuleType.SUBTYPE;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class TypeInferenceResolver {

    private TypeInferenceResolver() {

    }

    /**
     * Resolve unresolved variables in a list of bounds.
     */
    public static Map<Variable, JavaTypeDefinition> resolveVariables(List<Bound> bounds) {
        Map<Variable, Set<Variable>> variableDependencies = getVariableDependencies(bounds);
        Map<Variable, JavaTypeDefinition> instantiations = getInstantiations(bounds);

        List<Variable> uninstantiatedVariables = new ArrayList<>(getUninstantiatedVariables(bounds));

        // If every variable in V has an instantiation, then resolution succeeds and this procedure terminates.
        while (!uninstantiatedVariables.isEmpty()) {
            // "... ii) there exists no non-empty proper subset of { α1, ..., αn } with this property. ..."

            // Note: since the Combinations class enumerates the power set from least numerous to most numerous sets
            // the above requirement is satisfied
            for (List<Variable> variableSet : new Combinations(uninstantiatedVariables)) {
                if (isProperSubsetOfVariables(variableSet, instantiations, variableDependencies, bounds)) {
                    // TODO: resolve variables
                }
            }
        }


        return instantiations;
    }

    /**
     * Given a set of inference variables to resolve, let V be the union of this set and all variables upon which
     * the resolution of at least one variable in this set depends.
     * <p>
     * ...
     * <p>
     * Otherwise, let { α1, ..., αn } be a non-empty subset of uninstantiated variables in V such that i) for all
     * i (1 ≤ i ≤ n), if αi depends on the resolution of a variable β, then either β has an instantiation or
     * there is some j such that β = αj; and Resolution proceeds by generating an instantiation for each of α1,
     * ..., αn based on the bounds in the bound set:
     *
     * @return true, if 'variables' is a resolvable subset
     */
    public static boolean isProperSubsetOfVariables(List<Variable> variables,
                                                    Map<Variable, JavaTypeDefinition> instantiations,
                                                    Map<Variable, Set<Variable>> dependencies,
                                                    List<Bound> bounds) {

        // search the bounds for an
        for (Variable unresolvedVariable : variables) {
            for (Variable dependency : dependencies.get(unresolvedVariable)) {
                if (!instantiations.containsKey(dependency)
                        && !boundsHaveAnEqualityBetween(variables, dependency, bounds)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @return true, if 'bounds' contains an equality between 'second' and an element from 'firstList'
     */
    public static boolean boundsHaveAnEqualityBetween(List<Variable> firstList, Variable second, List<Bound> bounds) {
        for (Bound bound : bounds) {
            for (Variable first : firstList) {
                if (bound.ruleType == EQUALITY
                        && ((bound.leftVariable() == first && bound.rightVariable() == second)
                        || (bound.leftVariable() == second && bound.rightVariable() == first))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Makes it possible to iterate over the power set of a List. The order is from the least numerous
     * to the most numerous.
     * Example list: ABC
     * Order: A, B, C, AB, AC, BC, ABC
     */
    private static class Combinations implements Iterable<List<Variable>> {
        private int n;
        private int k;
        private List<Variable> permuteThis;
        private List<Variable> resultList = new ArrayList<>();
        private List<Variable> unmodifyableViewOfResult = Collections.unmodifiableList(resultList);

        public Combinations(List<Variable> permuteThis) {
            this.permuteThis = permuteThis;
            this.n = permuteThis.size();
            this.k = 0;
        }

        @Override
        public Iterator<List<Variable>> iterator() {
            return new Iterator<List<Variable>>() {
                private BitSet nextBitSet = new BitSet(n);

                {
                    advanceToNextK();
                }

                @Override
                public void remove() {

                }

                private void advanceToNextK() {
                    if (++k > n) {
                        nextBitSet = null;
                    } else {
                        nextBitSet.clear();
                        nextBitSet.set(0, k);
                    }
                }

                @Override
                public boolean hasNext() {
                    return nextBitSet != null;
                }

                @Override
                public List<Variable> next() {
                    BitSet resultBitSet = (BitSet) nextBitSet.clone();

                    int b = nextBitSet.previousClearBit(n - 1);
                    int b1 = nextBitSet.previousSetBit(b);

                    if (b1 == -1) {
                        advanceToNextK();
                    } else {
                        nextBitSet.clear(b1);
                        nextBitSet.set(b1 + 1, b1 + (n - b) + 1);
                        nextBitSet.clear(b1 + (n - b) + 1, n);
                    }

                    resultList.clear();
                    for (int i = 0; i < n; ++i) {
                        if (resultBitSet.get(i)) {
                            resultList.add(permuteThis.get(i));
                        }
                    }

                    return unmodifyableViewOfResult;
                }
            };
        }
    }


    /**
     * @return A map of variable -> proper type produced by searching for α = T or T = α bounds
     */
    public static Map<Variable, JavaTypeDefinition> getInstantiations(List<Bound> bounds) {
        Map<Variable, JavaTypeDefinition> result = new HashMap<>();

        // The term "type" is used loosely in this chapter to include type-like syntax that contains inference
        // variables. The term proper type excludes such "types" that mention inference variables. Assertions that
        // involve inference variables are assertions about every proper type that can be produced by replacing each
        // inference variable with a proper type.

        // Some bounds relate an inference variable to a proper type. Let T be a proper type. Given a bound of the
        // form α = T or T = α, we say T is an instantiation of α. Similarly, given a bound of the form α <: T, we
        // say T is a proper upper bound of α, and given a bound of the form T <: α, we say T is a proper lower bound
        // of α.
        for (Bound bound : bounds) {
            if (bound.ruleType() == EQUALITY) {
                // Note: JLS's wording is not clear, but proper type excludes arrays, nulls, primitives, etc.
                if (bound.isLeftVariable() && bound.isRightProper()) {
                    result.put(bound.leftVariable(), bound.rightProper());
                } else if (bound.isLeftProper() && bound.isRightVariable()) {
                    result.put(bound.rightVariable(), bound.leftProper());
                }
            }
        }

        return result;
    }

    /**
     * @return A list of variables which have no direct instantiations
     */
    public static Set<Variable> getUninstantiatedVariables(List<Bound> bounds) {
        Set<Variable> result = getMentionedVariables(bounds);
        result.removeAll(getInstantiations(bounds).keySet());
        return result;
    }

    public static Map<Variable, Set<Variable>> getVariableDependencies(List<Bound> bounds) {
        Map<Variable, Set<Variable>> dependencies = new HashMap<>();

        for (Variable mentionedVariable : getMentionedVariables(bounds)) {
            Set<Variable> set = new HashSet<>();
            // An inference variable α depends on the resolution of itself.
            set.add(mentionedVariable);

            dependencies.put(mentionedVariable, set);
        }

        // produce initial dependencies
        for (Bound bound : bounds) {
            // Given a bound of one of the following forms, where T is either an inference variable β or a type that
            // mentions β:

            if (bound.leftVariable() != null && bound.rightHasMentionedVariable()) {
                if (bound.ruleType == EQUALITY || bound.ruleType() == SUBTYPE) {
                    // α = T
                    // α <: T
                    dependencies.get(bound.leftVariable()).add(bound.getRightMentionedVariable());
                }
            } else if (bound.leftHasMentionedVariable() && bound.rightVariable() != null) {
                if (bound.ruleType == EQUALITY || bound.ruleType() == SUBTYPE) {
                    // T = α
                    // T <: α
                    dependencies.get(bound.getLeftMentionedVariable()).add(bound.rightVariable());
                }
            }

            // If α appears on the left-hand side of another bound of the form G<..., α, ...> = capture(G<...>), then
            // β depends on the resolution of α. Otherwise, α depends on the resolution of β. TODO

            // An inference variable α appearing on the left-hand side of a bound of the form G<..., α, ...> =
            // capture(G<...>) depends on the resolution of every other inference variable mentioned in this bound
            // (on both sides of the = sign). TODO
        }


        // An inference variable α depends on the resolution of an inference variable β if there exists an inference
        // variable γ such that α depends on the resolution of γ and γ depends on the resolution of β.

        for (int i = 0; i < dependencies.size(); ++i) { // do this n times, where n is the count of variables
            boolean noMoreChanges = true;

            for (Map.Entry<Variable, Set<Variable>> entry : dependencies.entrySet()) {
                // take the Variable's dependency list
                for (Variable variable : entry.getValue()) {
                    // add those variable's dependencies
                    if (entry.getValue().addAll(dependencies.get(variable))) {
                        noMoreChanges = false;
                    }
                }
            }

            if (noMoreChanges) {
                break;
            }
        }

        return dependencies;
    }

    /**
     * @return a set of variables mentioned by the bounds
     */
    public static Set<Variable> getMentionedVariables(List<Bound> bounds) {
        Set<Variable> result = new HashSet<>();

        for (Bound bound : bounds) {
            bound.addVariablesToSet(result);
        }

        return result;
    }

    /**
     * https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.3
     */
    public static List<Constraint> incorporateBounds(List<Bound> currentBounds, List<Bound> newBounds) {
        // (In this section, S and T are inference variables or types, and U is a proper type. For conciseness, a bound
        // of the form α = T may also match a bound of the form T = α.)

        List<Constraint> newConstraints = new ArrayList<>();

        for (Bound first : currentBounds) {
            for (Bound second : newBounds) {
                Sides sides = getUnequalSides(first, second);
                if (sides == null) {
                    continue;
                }

                if (first.ruleType() == EQUALITY && second.ruleType() == EQUALITY) {
                    // α = S and α = T imply ‹S = T›
                    newConstraints.add(copyConstraint(first, second, getUnequalSides(first, second), EQUALITY));
                } else if (first.ruleType() == EQUALITY && second.ruleType() == SUBTYPE) {
                    if (sides.second == Side.RIGHT) {
                        // α = S and α <: T imply ‹S <: T›
                        newConstraints.add(copyConstraint(first, second, sides, SUBTYPE));
                    } else {
                        // α = S and T <: α imply ‹T <: S›
                        newConstraints.add(copyConstraint(second, first, sides.copySwap(), SUBTYPE));
                    }

                } else if (first.ruleType() == SUBTYPE && second.ruleType() == EQUALITY) {
                    if (sides.first == Side.RIGHT) {
                        // α <: T and α = S imply ‹S <: T›
                        newConstraints.add(copyConstraint(second, first, sides.copySwap(), SUBTYPE));
                    } else {
                        // T <: α and α = S imply ‹T <: S›
                        newConstraints.add(copyConstraint(first, second, sides, SUBTYPE));
                    }

                } else if (first.ruleType() == SUBTYPE && second.ruleType() == SUBTYPE) {
                    if (sides.first == Side.LEFT && sides.second == Side.RIGHT) {
                        // S <: α and α <: T imply ‹S <: T›
                        newConstraints.add(copyConstraint(first, second, sides, SUBTYPE));
                    } else if (sides.first == Side.RIGHT && sides.second == Side.LEFT) {
                        // α <: T and S <: α imply ‹S <: T›
                        newConstraints.add(copyConstraint(second, first, sides.copySwap(), SUBTYPE));
                    }
                }


                // α = U and S = T imply ‹S[α:=U] = T[α:=U]› TODO

                // α = U and S <: T imply ‹S[α:=U] <: T[α:=U]› TODO
            }
        }

        return newConstraints;
    }

    private enum Side {
        LEFT, RIGHT
    }

    private static class Sides {
        /* default */ final Side first;
        /* default */ final Side second;

        /* default */ Sides(Side first, Side second) {
            this.first = first;
            this.second = second;
        }

        /* default */ Sides copySwap() {
            return new Sides(second, first);
        }
    }

    private static Sides getUnequalSides(BoundOrConstraint first, BoundOrConstraint second) {
        if (first.leftVariable() != null) {
            if (first.leftVariable() == second.leftVariable()) {
                return new Sides(Side.RIGHT, Side.RIGHT);
            } else if (first.leftVariable() == second.rightVariable()) {
                return new Sides(Side.RIGHT, Side.LEFT);
            }
        } else if (first.rightVariable() != null) {
            if (first.rightVariable() == second.leftVariable()) {
                return new Sides(Side.LEFT, Side.RIGHT);
            } else if (first.rightVariable() == second.rightVariable()) {
                return new Sides(Side.LEFT, Side.LEFT);
            }
        }

        return null;
    }

    private static Constraint copyConstraint(BoundOrConstraint first, BoundOrConstraint second, Sides sides,
                                             InferenceRuleType rule) {
        if (sides.first == Side.LEFT) {
            if (sides.second == Side.LEFT) {
                if (first.leftVariable() != null) {
                    if (second.leftVariable() != null) {
                        return new Constraint(first.leftVariable(), second.leftVariable(), rule);
                    } else {
                        return new Constraint(first.leftVariable(), second.leftProper(), rule);
                    }
                } else {
                    if (second.leftVariable() != null) {
                        return new Constraint(first.leftProper(), second.leftVariable(), rule);
                    } else {
                        return new Constraint(first.leftProper(), second.leftProper(), rule);
                    }
                }
            } else {
                if (first.leftVariable() != null) {
                    if (second.rightVariable() != null) {
                        return new Constraint(first.leftVariable(), second.rightVariable(), rule);
                    } else {
                        return new Constraint(first.leftVariable(), second.rightProper(), rule);
                    }
                } else {
                    if (second.rightVariable() != null) {
                        return new Constraint(first.leftProper(), second.rightVariable(), rule);
                    } else {
                        return new Constraint(first.leftProper(), second.rightProper(), rule);
                    }
                }
            }
        } else {
            if (sides.second == Side.LEFT) {
                if (first.rightVariable() != null) {
                    if (second.leftVariable() != null) {
                        return new Constraint(first.rightVariable(), second.leftVariable(), rule);
                    } else {
                        return new Constraint(first.rightVariable(), second.leftProper(), rule);
                    }
                } else {
                    if (second.leftVariable() != null) {
                        return new Constraint(first.rightProper(), second.leftVariable(), rule);
                    } else {
                        return new Constraint(first.rightProper(), second.leftProper(), rule);
                    }
                }
            } else {
                if (first.rightVariable() != null) {
                    if (second.rightVariable() != null) {
                        return new Constraint(first.rightVariable(), second.rightVariable(), rule);
                    } else {
                        return new Constraint(first.rightVariable(), second.rightProper(), rule);
                    }
                } else {
                    if (second.rightVariable() != null) {
                        return new Constraint(first.rightProper(), second.rightVariable(), rule);
                    } else {
                        return new Constraint(first.rightProper(), second.rightProper(), rule);
                    }
                }
            }
        }
    }
}
