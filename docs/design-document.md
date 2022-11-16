# Design Document Template for the RDS Atlas Team

In our specific case, where we will build and add features to multiple drivers, the title of any design document should be clear on:
- which feature is the document about
- which environment/application the feature is being applied to

A good format for a clear title is:  `[Feature short name] for [Driver name]`

Examples:
```
- Connection failover for the AWS JDBC MySQL Driver
- Read-Write splitting for the AWS JDBC Wrapper
- IAM authentication for the AWS ODBC MySQL Driver
- Automated timesheets for Workday
```

# Introduction

Every document begins with an introduction. In the introduction, the writer should go all the way back to initial state of the service/application and explain why the new feature is important and required.

In 2 (max 3) paragraphs, the writer should quickly introduce the service/application, and briefly explain how it works ***before*** introducing the new feature. If the new feature will use a new technology or will interact with a 3rd party tool or service, those should also be briefly introduced here.

Another thing to mention in the introduction is to briefly mention the reasons why the new feature is required. Whether it is a mandatory feature, or something that has been requested by the users multiple times, it is worth adding a couple of lines to help the reader to understand the importance and the impact of the new feature.

The Introduction section can often be merged with the problem section. There is actually no rule on whether to have them merged or not. If the introduction gets a bit longer, as it has to introduce and describe multiple services or applications, it can be worth splitting those sections to add a quick break in the read. When the introduction is shorter, one can simply state the problem in the end of it and move on to the goals.

# Glossary

The glossary is a section to explain or clarify the meaning of any acronyms and or technical terms that are used in the rest of the document. It is not a mandatory section but recommended if the document rely on some specific technical terms that may not be well known to some users/readers.

Example:
```
multi-AZ: application/service hosted in multiple Availability zones
IoT: Internet of Things
```

## Acronyms

It is very common that a feature/component/strategy defined in the document has a long name often referred by an acronym. If the document refers to that acronym multiple times, it is recommended to use the component full name the first time it appears in the document, followed by the acronym in parenthesis. This allows the reader to search back for its name/meaning if needed.

Acronyms are often introduced either in the Introduction section, or inside a Solution (Proposed or Discarded) subsection.

Examples:
```
Earlier this year, we have introduced Enhanced Failover Monitoring (EFM), a mechanism that
allows the driver to quickly identify a cluster failover and therefore trigger a driver failover.

... (later in the doc)

One of the advantages of EFM is that there is only one monitoring thread per node in
the cluster, no matter how many different connections are open to this node.

```

# Problem

The problem section is a paragraph, usually a one-liner, where the writer clearly defines the problem the document is addressing.

When Introduction and Problem sections are merged, the Introduction should finish with one line where it clearly states the problem the document is trying to address.

Ex:
```
This documents addresses the issue of our team not being able to organize
team lunches by proposing the introduction of a team lunch schedule.
```

# Goal(s)

The Goal (or Goals) section highlights the achievements that the writer expects to obtain by executing/implementing the solution that will be proposed in the document.

A goal should be an atomic achievement that is clearly described, and also easily verifiable. People that will read the document will judge the quality of the proposed solution according to the effectiveness it will have to achieve the goals listed in this section.

When goals involve measurements, it is recommended to include the numeric values desired when listing the project goals. Unclear or ambiguous value will very often lead to questions from the readers.

Example:
```
- Reduce the service response time - NOT GOOD (What is the expected response time?)
- Reduce significantly the service response time - UNCLEAR (What does significantly mean?)
- Reduce the service response time below 200ms - GOOD (Goal is clear)
```

When a project has multiple goals, it is common to list them one by one, either in priority order, either in execution order, in a list.

Examples:
```
(Priority)
In order to succesfully decrease the service response time, our goals are to:
- Identify and fix the memory leaks in the codebase
- Replace the database driver to a newer version
- Upgrade the disk space and memory of our cloud instances
```

```
(Execution order)
In order to succesfully write a document, our goals are to:
- Write the introduction
- Write the core part of the document
- Insert an inspiring little doodling in the middle
- Do a barrel roll
- Write the conclusion
```

# Non-Goals

The Non-Goals section is not the opposite of the Goals section, but more like another way of limiting the scope of the document.

Non-Goals are basically goals that are not a priority or will not be addressed in the scope of this specific document. Non-goals are very often things that would be nice to have achieved though.

Example:
```
Non-Goals for this design document are:
- Not increasing the server response time
- Add a public API to interact with the new feature
- Reduce the amount of support tickets associated to the feature
```

The Non-Goals section is not mandatory in a document (it is not even very frequent), but it is handy to prevent future questions that a reader might raise when reading the proposed solution, such as `"Does your solution does something to address/prevent X?"` or `"Does your solution does something to reduce response/connection time?"`

# Requirements/Constraints

Requirements and/or Constraints are details that were defined prior to the design phase and that have an impact on the choice of proposed solution.

Common cases of sources for those requirement/constraints are:
- Licence compatibility
- Available/Unavailable resources
- Decisions made by managers/PMs

Examples:
```
For this project, a requirement is to design a solution that addresses X without
adding any dependency to any 3rd party library using the GPL licence.
```
```
For this project, a requirement is to keep the software image size below
650MB as it will be shipped to our partners in CDs.
```
```
For this project, it was requested by the company board that languages like
Python and Ruby were avoided due to recent security issues found in the SSL
libraries for those languages.
```

Requirements often have a role on choosing which approach to use when addressing the problem, and even more often on defining which solutions to discard. When the requirements were not design choices made by the writer, it is important to clearly state those ***before*** starting describing the proposed and discarded solutions, so the reader will have the opportunity to validate the solutions against the requirements and constraints.

# Solutions

Here's the beginning of the critical section of the document.

Everything written prior to this point had purpose of stating the problem this solution is addressing and providing a way for the reader to verify that your solution satisfies the problem.

## No conclusion

The solutions section should close the document. As it drives the reader into a more technical scope, it is not recommended to add any kind of text after this section. Conclusions are often either redundant or not required.

Example:
```
SOLUTION SECTION

In order to ensure that the waiting line does not goes over 10 people, we will immediately
schedule an appointment for any person that accesses the website for the following day.

CONCLUSION

In conclusion, we believe that the solution is the most appropriate as it addresses
the issue of not letting the waiting line to go over 10 people at any time.
```

The conclusion in the example above is redundant, as it not only paraphrases what was probably already mentioned in the Goals section, but also repeats something that was previously mentioned when describing the solution.

***As a rule of thumb, the writer should always let the solution speak for itself.***

## Presentation order

There is no correct order between the presentation of discarded and proposed solutions. There is however situations where the writer will want to prefer to have one section or the other written first.

As the document is gonna be read from the top to the bottom, it is important to start the solutions section with the solution that the writer want to be discussed more.

If the goal of the document is to present a solution that was already defined as the one that better addresses the problem, and the writer is more interested in validation/feedback instead of being questioned about the choice of solution, the writer should definitely present the Proposed Solution section before the discarded solutions. In this situation, the reader will read the entire solution before going to check if there were any alternatives studied or discarded choices.

In the other hand, if the document goal is to explain why some specific solutions previously proposed do not fully address the problem discarded, or do not respect the requirements and constrains of the project, one should perhaps start the solution section by describing the solutions that were studied and should not be adopted. In this case, the reader will have an idea of all the discarded solutions and their reasons before reaching the final solution proposed in the document.

## Proposed Solution

The goal of this subsection is to:
- Detail your solution so the reader has a clear idea of the changes being introduced
- Describe how your changes impact the system and where those changes will be made
- Explain how your solution satisfies the goals and requirements/constraints presented in the document
- Convince the audience that the solution is indeed the better suited solution for the problem

### Diagrams

When presenting a design, very often the writer will require diagrams to explain a solution. That is because very often visual elements are extremely helpful for people to understand concepts or integrations that would perhaps be complicated to describe with words.

The most common diagrams one might need in a design document are:
- A global architecture diagram
- A sequence diagram
- A class/entity diagram

When presenting a solution with multiple diagrams, it is recommended to start from the global architecture diagram, with is less specific and shows the system as a whole. Once the writer has explained how the feature will be implemented in the larger scope, it is then safe to proceed with the other diagrams that go more in detail in the implementation of the solution.

[Still in progress]

## Discarded Solutions

The discarded solutions section should list all the solutions that were potential solutions to address the problem but were discarded for some reason.

In this section, the writer should create a subsection for each discarded solution that will be presented. For each of those entries, it is recommended to give an name to the solution that summarizes or what made the approach unique.

Example:
```
Discarded Solutions
- Solution A: using an external cache
- Solution B: using a distributed hashmap
```

Inside a solution subsection, the write should describe the solution just as if it was the proposed one. That is in case some reader still prefers that solution over the proposed one.

However, unlike a proposed solution, the writer should also remember to clearly state the reasons the solution was discarded.

There are multiple reasons for a solution to get discarded:
- The solution does not address the problem or achieve the goals previously stated\
  The writer should highlight the elements (or the lack of) that show that the solution does not satisfy the goals the document wants to achieve.\
  Examples:\
  `This solution was discarded as while it would be effective for our premium users, it would not reach potential new customers currently in a free plan as they would not have access to this feature. The goal of the project was to make the new feature accessible to all our users independently of location or enrollment status.`

- The solution does not respect some of the requirements or constraints previously specified\
  Just like the previous case, the writer should clearly identify the pieces of the solution that violate the constraints for the project.
  Examples:\
  `This solution was discarded as it would add a round-trip connection to an external microservice that would increase the server response time above 200ms, which does not satisfy a requirement for this project.`

- The solution is valid (or could work) but costs more in resources/time/money\
  In this case, a bit of argumentation added to some quantitative information about it will be required. If the matter is time, then it is expected that all valid solutions should have had a rough estimation provided. In case of money/resources, the difference or gap between sections should also be mentioned.\
  Those cases generally trigger discussions and questions from the audience/readers.\
  Examples:\
  `This solution is less preferable as its estimation of completion time would be around 6 months, instead of 3 months for other solutions.`\
  `Creating a new microservice instead of extending an existing one will increase the amount of devices in our cloud fleet and therefore the size of the infrastructure to be monitored by SRE/DevOps team.`

- The solution is valid but not the one preferred by the writer\
  There are many valid reasons to prefer one solution to another. Strong argumentation will be necessary to convince the reader that it is the correct choice, as it is very likely to be questioned in a feedback session.\
  If a solution comes with positive aspects but also brings up some negative ones, it is also a good idea to introduce a `Pros/Cons` subsection after the solution is detailed.

# Appendix

Appendix is a part at the end of the document where the writer can add anything that might have value to the document/discussion but will make the document length too large/bulky.

It is often used for large diagrams, code snippets, or the integrality of performance tests results.

Every piece of information added to the appendix should be given an identifying reference (often numbers or letters) and a name.

Example:
```
Appendix A - Recipy for a nice spaghetti bolognaise

Recipy:
Boil water
Add pasta
Wait 8 minutes
Add bolognaise sauce
Put sauce on top of pasta (not too much)
Add meatballs (optional)
Serve it hot
```

```
Appendix B - Very nice house

                                   /\
                              /\  //\\
                       /\    //\\///\\\        /\
                      //\\  ///\////\\\\  /\  //\\
         /\          /  ^ \/^ ^/^  ^  ^ \/^ \/  ^ \
        / ^\    /\  / ^   /  ^/ ^ ^ ^   ^\ ^/  ^^  \
       /^   \  / ^\/ ^ ^   ^ / ^  ^    ^  \/ ^   ^  \       *
      /  ^ ^ \/^  ^\ ^ ^ ^   ^  ^   ^   ____  ^   ^  \     /|\
     / ^ ^  ^ \ ^  _\___________________|  |_____^ ^  \   /||o\
    / ^^  ^ ^ ^\  /______________________________\ ^ ^ \ /|o|||\
   /  ^  ^^ ^ ^  /________________________________\  ^  /|||||o|\
  /^ ^  ^ ^^  ^    ||___|___||||||||||||___|__|||      /||o||||||\
 / ^   ^   ^    ^  ||___|___||||||||||||___|__|||          | |
/ ^ ^ ^  ^  ^  ^   ||||||||||||||||||||||||||||||oooooooooo| |ooooooo
ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo
```

It is important to add a reference in the document where having a look at a specific appendix might explain or make the situation clearer.

Example:
```
The choice of font in the recipy book was driven by the fact that cooking recipies
are often a long set of sequential operations, but often very simple (cf. Appendix A). 
```
