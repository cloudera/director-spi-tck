# Cloudera Director - Cloud Provider TCK

This technology compatibility kit (TCK) checks that an implementation of a
cloud provider plugin for [Cloudera Director][cd] complies with expectations for
plugins and works correctly.

The TCK contains one set of tests for each version of the
[Cloudera Director plugin SPI][spi].

# Building

```
mvn -DskipTests install
```

# Running

Run the TCK from the command line. Two arguments are required: the path to
the plugin JAR to test, and the path to a configuration file that will drive
the test.

```
java -jar target/director-spi-tck-*.jar /path/to/xyz-provider.jar xyz.conf
```

## v1 Configuration File

The configuration file for the v1 TCK is a [HOCON][hocon] (a superset of JSON)
file that contains the settings the TCK needs to use the plugin. Here is an
example configuration file, similar to what is used for testing an Amazon Web
Services plugin.

```
configurationDirectory: /tmp

aws {

  configs {
    accessKeyId: AAAAAAAAAAAAAAAAAAAA
    secretAccessKey: "abcdefabcdefabcdefabcdefabcdefabcdefabcd"
  }

  com.cloudera.director.aws.ec2.EC2Provider {

    configs {
      region: us-west-1
    }

    resourceConfigs {
      type: t2.micro
      image: ami-abcd1234
      subnetId: subnet-abcd1234
      securityGroupsIds: sg-abcd1234
      instanceNamePrefix: spi-tck-${?USER}
    }

    resourceTags {
      owner: ${?USER}
    }

    expectedOpenPort: 22
  }

  com.cloudera.director.aws.rds.RDSProvider {

    configs {
      rdsRegion: us-west-1
    }

    resourceConfigs {
      type: MYSQL
      adminUsername: root
      adminPassword: password
      instanceClass: db.t1.micro
      engineVersion: 5.5.41
      dbSubnetGroupName: all-subnets
      vpcSecurityGroupIds: "sg-abcd1234,sg-a1b2c3d4,sg-1234abcd"
      allocatedStorage: 10
      backupRetentionPeriod: 0
      skipFinalSnapshot: true
    }

    resourceTags {
      owner: ${?USER}
    }

    expectedOpenPort: 3306
  }
}
```

The `configurationDirectory` is passed to the plugin launcher's `initialize`
method. If the plugin does not require anything in the configuration directory,
any empty directory will do.

Include one main section named with the ID of the `CloudProvider`
implementation, as returned in its metadata. Within the main section, include a
`configs` section with configuration information needed for the plugin launcher
to create the cloud provider object.

Then, include within the main section one section for each resource provider
available in the plugin, named with the resource providers' class names. In
these resource provider sections:

* Include a `configs` section with configuration information needed for the
  cloud provider to create the resource provider.
* Include a `resourceConfigs` section with configuration information needed for
  the resource provider to create a resource template.
* Include the number for a port that should be open once a resource has been
  allocated by the resource provider. Use `-1` to have the TCK skip the port
  test.

# Important notice

Copyright &copy; 2015 Cloudera, Inc. Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Cloudera, the Cloudera logo, and any other product or service names or slogans contained in this document are trademarks of Cloudera and its suppliers or licensors, and may not be copied, imitated or used, in whole or in part, without the prior written permission of Cloudera or the applicable trademark holder.

Hadoop and the Hadoop elephant logo are trademarks of the Apache Software Foundation. Amazon Web Services, the "Powered by Amazon Web Services" logo, Amazon Elastic Compute Cloud, EC2, Amazon Relational Database Service, and RDS are trademarks of Amazon.com, Inc. or its affiliates in the United States and/or other countries. All other trademarks, registered trademarks, product names and company names or logos mentioned in this document are the property of their respective owners. Reference to any products, services, processes or other information, by trade name, trademark, manufacturer, supplier or otherwise does not constitute or imply endorsement, sponsorship or recommendation thereof by us.

Complying with all applicable copyright laws is the responsibility of the user. Without limiting the rights under copyright, no part of this document may be reproduced, stored in or introduced into a retrieval system, or transmitted in any form or by any means (electronic, mechanical, photocopying, recording, or otherwise), or for any purpose, without the express written permission of Cloudera.

Cloudera may have patents, patent applications, trademarks, copyrights, or other intellectual property rights covering subject matter in this document. Except as expressly provided in any written license agreement from Cloudera, the furnishing of this document does not give you any license to these patents, trademarks, copyrights, or other intellectual property. For information about patents covering Cloudera products, see http://tiny.cloudera.com/patents.

The information in this document is subject to change without notice. Cloudera shall not be liable for any damages resulting from technical errors or omissions which may be present in this document, or from use of this document.

[cd]: http://www.cloudera.com/director
[spi]: https://github.com/cloudera/director-spi
[hocon]: https://github.com/typesafehub/config/blob/master/HOCON.md
